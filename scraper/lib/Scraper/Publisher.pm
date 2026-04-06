package Scraper::Publisher;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use Mojo::JSON qw(encode_json);

sub new {
  my ($class, %args) = @_;

  croak 'redis_db is required' unless $args{redis_db};
  croak 'ingestion_stream is required' unless $args{ingestion_stream};
  croak 'dlq_stream is required' unless $args{dlq_stream};

  return bless {
    redis_db          => $args{redis_db},
    ingestion_stream  => $args{ingestion_stream},
    dlq_stream        => $args{dlq_stream},
  }, $class;
}

sub publish_job {
  my ($self, $job) = @_;
  my $payload = ref $job ? encode_json($job) : $job;
  return $self->_xadd_with_retry($self->{ingestion_stream}, $payload, 2);
}

sub publish_dlq {
  my ($self, $payload) = @_;
  my $encoded = ref $payload ? encode_json($payload) : $payload;
  return $self->_xadd_with_retry($self->{dlq_stream}, $encoded, 1);
}

sub _xadd_with_retry {
  my ($self, $stream, $payload, $attempts) = @_;
  my $error;

  for my $attempt (1 .. $attempts) {
    my $result = eval {
      return $self->{redis_db}->xadd($stream, '*', payload => $payload);
    };

    return $result if !$@;
    $error = $@;
  }

  die $error;
}

1;
