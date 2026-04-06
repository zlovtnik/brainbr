package Test::FakeRedis;

use strict;
use warnings;
use v5.30;

sub new {
  my ($class) = @_;
  return bless {
    hashes   => {},
    sets     => {},
    strings  => {},
    expiry   => {},
    streams  => {},
    last_ids => {},
    txn      => undef,
  }, $class;
}

sub hget {
  my ($self, $key, $field) = @_;
  return $self->{hashes}{$key}{$field};
}

sub hset {
  my ($self, $key, $field, $value) = @_;
  $self->{hashes}{$key}{$field} = $value;
  return 1;
}

sub sadd {
  my ($self, $key, $member) = @_;
  my $exists = exists $self->{sets}{$key}{$member} ? 1 : 0;
  $self->{sets}{$key}{$member} = 1;
  return $exists ? 0 : 1;
}

sub srem {
  my ($self, $key, $member) = @_;
  my $exists = delete $self->{sets}{$key}{$member};
  return $exists ? 1 : 0;
}

sub smembers {
  my ($self, $key) = @_;
  return sort keys %{ $self->{sets}{$key} // {} };
}

sub expire {
  my ($self, $key, $ttl) = @_;
  $self->{expiry}{$key} = $ttl;
  return 1;
}

sub ttl {
  my ($self, $key) = @_;
  my $exists = exists $self->{hashes}{$key}
            || exists $self->{sets}{$key}
            || exists $self->{strings}{$key}
            || exists $self->{streams}{$key};
  return -2 unless $exists;
  return -1 unless exists $self->{expiry}{$key};
  my $remaining = $self->{expiry}{$key} - time();
  return $remaining < 0 ? 0 : int($remaining);
}

sub set {
  my ($self, $key, $value, @options) = @_;

  my %opts;
  while (@options) {
    my $token = shift @options;
    if ($token =~ /\A(?:EX|PX)\z/) {
      $opts{$token} = shift @options;
      next;
    }

    $opts{$token} = 1;
  }

  return undef if $opts{NX} && exists $self->{strings}{$key};

  $self->{strings}{$key} = $value;
  if (exists $opts{PX}) {
    $self->{expiry}{$key} = int($opts{PX} / 1000);
  }
  elsif (exists $opts{EX}) {
    $self->{expiry}{$key} = $opts{EX};
  }
  else {
    delete $self->{expiry}{$key};
  }
  return 'OK';
}

sub del {
  my ($self, $key) = @_;
  delete $self->{hashes}{$key};
  delete $self->{sets}{$key};
  delete $self->{strings}{$key};
  delete $self->{expiry}{$key};
  delete $self->{streams}{$key};
  delete $self->{last_ids}{$key};
  return 1;
}

sub xadd {
  my ($self, $stream, $id, %fields) = @_;
  if (!defined $id || $id eq '*' || $id eq '+') {
    my $seq = ++$self->{last_ids}{$stream};
    my $ms = int(time * 1000);
    $id = sprintf '%d-%d', $ms, $seq;
  }
  push @{$self->{streams}{$stream}}, {
    id     => $id,
    fields => {%fields},
  };
  return $id;
}

sub multi {
  my ($self) = @_;
  if ($self->{_fail_txn}) {
    return undef;
  }
  $self->{txn} = 1;
  return 'OK';
}

sub exec {
  my ($self) = @_;
  $self->{txn} = undef;
  if ($self->{_fail_txn}) {
    die "EXECABORT Transaction discarded\n";
  }
  return [];
}

sub set_fail_txn {
  my ($self, $fail) = @_;
  $self->{_fail_txn} = $fail ? 1 : 0;
}

sub discard {
  my ($self) = @_;
  $self->{txn} = undef;
  return 'OK';
}

1;
