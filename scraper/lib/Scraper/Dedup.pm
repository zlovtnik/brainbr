package Scraper::Dedup;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use Digest::SHA qw(sha256_hex);

sub new {
  my ($class, %args) = @_;

  croak 'redis_db is required' unless $args{redis_db};

  return bless {
    redis_db     => $args{redis_db},
    ttl_seconds  => int($args{ttl_seconds} // (90 * 24 * 60 * 60)),
  }, $class;
}

sub inspect {
  my ($self, $source_id, $law_ref, $raw_content) = @_;

  croak 'source_id is required' unless defined $source_id && length $source_id;
  croak 'law_ref is required' unless defined $law_ref && length $law_ref;

  my $digest = sha256_hex(join "\x1E", $law_ref, ($raw_content // q{}));
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

  if (defined $previous_digest && length $previous_digest && $previous_digest ne $digest) {
    $self->{redis_db}->srem($set_key, $previous_digest);
  }

  my $added = $self->{redis_db}->sadd($set_key, $digest);
  $self->{redis_db}->hset($map_key, $law_ref, $digest);
  $self->_refresh($set_key, $map_key);

  return $added ? 1 : 0;
}

sub _refresh {
  my ($self, @keys) = @_;
  for my $key (@keys) {
    $self->{redis_db}->expire($key, $self->{ttl_seconds});
  }
}

1;
