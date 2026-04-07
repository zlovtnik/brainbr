package Scraper::Dedup;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use Digest::SHA qw(sha256_hex);
use Encode qw(encode_utf8);

sub new {
  my ($class, %args) = @_;

  croak 'redis_db is required' unless $args{redis_db};
  my $default_ttl = 90 * 24 * 60 * 60;
  my $ttl_seconds;
  if (exists $args{ttl_seconds}) {
    croak 'ttl_seconds must be a positive integer'
      unless defined $args{ttl_seconds}
          && $args{ttl_seconds} =~ /\A\d+\z/
          && $args{ttl_seconds} > 0;
    $ttl_seconds = int($args{ttl_seconds});
  }
  else {
    $ttl_seconds = $default_ttl;
  }

  return bless {
    redis_db     => $args{redis_db},
    ttl_seconds  => $ttl_seconds,
  }, $class;
}

sub inspect {
  my ($self, $source_id, $law_ref, $raw_content) = @_;

  croak 'source_id is required' unless defined $source_id && length $source_id;
  croak 'law_ref is required' unless defined $law_ref && length $law_ref;

  my $digest = sha256_hex(encode_utf8(join "\x1E", $law_ref, ($raw_content // q{})));
  my $set_key = "scraper:seen:$source_id";
  my $map_key = "scraper:seen:$source_id:law_ref";

  my $existing = $self->{redis_db}->hget($map_key, $law_ref);

  if (defined $existing && length $existing && $existing eq $digest) {
    return {
      set_key          => $set_key,
      map_key          => $map_key,
      digest          => $digest,
      previous_digest => $existing,
      changed         => 0,
      is_new          => 0,
      should_enqueue  => 0,
    };
  }

  return {
    set_key          => $set_key,
    map_key          => $map_key,
    digest          => $digest,
    previous_digest => $existing,
    changed         => (defined $existing && length $existing && $existing ne $digest) ? 1 : 0,
    is_new          => (defined $existing && length $existing) ? 0 : 1,
    should_enqueue  => 1,
  };
}

sub commit {
  my ($self, $source_id, $law_ref, $digest, $previous_digest) = @_;

  croak 'source_id is required' unless defined $source_id && length $source_id;
  croak 'law_ref is required' unless defined $law_ref && length $law_ref;
  croak 'digest is required' unless defined $digest && length $digest;

  my $set_key = "scraper:seen:$source_id";
  my $map_key = "scraper:seen:$source_id:law_ref";

  if ($self->{redis_db}->can('multi') && $self->{redis_db}->can('exec')) {
    my $ok = eval {
      my $multi_ok = $self->{redis_db}->multi;
      die "multi() failed\n" unless $multi_ok;

      if (defined $previous_digest && length $previous_digest && $previous_digest ne $digest) {
        $self->{redis_db}->srem($set_key, $previous_digest);
      }
      $self->{redis_db}->sadd($set_key, $digest);
      $self->{redis_db}->hset($map_key, $law_ref, $digest);
      $self->{redis_db}->expire($set_key, $self->{ttl_seconds});
      $self->{redis_db}->expire($map_key, $self->{ttl_seconds});

      my $results = $self->{redis_db}->exec;
      for my $r (@{$results // []}) {
        die "Redis command error in transaction: $r\n"
          if ref(\$r) eq 'SCALAR' && $r =~ /\AERR/;
      }
      1;
    };

    if (!$ok) {
      my $error = $@;
      eval { $self->{redis_db}->discard if $self->{redis_db}->can('discard'); 1 };
      die $error;
    }
  }
  else {
    if (defined $previous_digest && length $previous_digest && $previous_digest ne $digest) {
      $self->{redis_db}->srem($set_key, $previous_digest);
    }
    $self->{redis_db}->sadd($set_key, $digest);
    $self->{redis_db}->hset($map_key, $law_ref, $digest);
    $self->_refresh($set_key, $map_key);
  }

  return 1;
}

sub _refresh {
  my ($self, @keys) = @_;
  for my $key (@keys) {
    $self->{redis_db}->expire($key, $self->{ttl_seconds});
  }
}

1;
