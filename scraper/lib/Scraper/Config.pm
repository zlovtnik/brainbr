package Scraper::Config;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use Mojo::File qw(path);
use YAML::XS qw(LoadFile);

sub from_env {
  my ($class, %args) = @_;

  my $root_dir = path($args{root} // path(__FILE__)->dirname->dirname->dirname);
  my $sources_file = $ENV{SCRAPER_SOURCES_FILE} // 'config/sources.yaml';
  my $sources_path = path($sources_file);
  $sources_path = $root_dir->child($sources_file) unless $sources_path->is_abs;

  my $redis_url = $ENV{REDIS_URL}
    or croak 'REDIS_URL must be set for the scraper service';

  my $raw = LoadFile($sources_path->to_string);
  my $sources = ref $raw eq 'HASH' ? $raw->{sources} : $raw;

  croak "Expected a 'sources' list in " . $sources_path unless ref $sources eq 'ARRAY';

  my @sources = map { _normalise_source($_) } @$sources;

  my $timeout_s = int($ENV{SCRAPER_REQUEST_TIMEOUT_S} // 30);
  my $max_retries = int($ENV{SCRAPER_MAX_RETRIES} // 3);

  return bless {
    root_dir             => $root_dir->to_string,
    redis_url            => $redis_url,
    ingestion_stream     => $ENV{INGESTION_STREAM} // 'queue_ingestion',
    ingestion_dlq        => $ENV{INGESTION_DLQ} // 'queue_ingestion_dlq',
    sources_file         => $sources_path->to_string,
    scraper_user_agent   => $ENV{SCRAPER_USER_AGENT} // 'FiscalBrain-Scraper/1.0',
    request_timeout_s    => $timeout_s,
    max_retries          => $max_retries,
    log_level            => lc($ENV{LOG_LEVEL} // 'info'),
    bootstrap_on_start   => _env_truthy($ENV{SCRAPER_BOOTSTRAP_ON_START} // 1),
    schedule_bootstrap_ttl_s => 600,
    schedule_lock_ttl_s  => ($timeout_s * $max_retries) + 60,
    dedup_ttl_seconds    => 90 * 24 * 60 * 60,
    sources              => \@sources,
  }, $class;
}

sub root_dir           { return shift->{root_dir}; }
sub redis_url          { return shift->{redis_url}; }
sub ingestion_stream   { return shift->{ingestion_stream}; }
sub ingestion_dlq      { return shift->{ingestion_dlq}; }
sub sources_file       { return shift->{sources_file}; }
sub scraper_user_agent { return shift->{scraper_user_agent}; }
sub request_timeout_s  { return shift->{request_timeout_s}; }
sub max_retries        { return shift->{max_retries}; }
sub log_level          { return shift->{log_level}; }
sub bootstrap_on_start { return shift->{bootstrap_on_start}; }
sub schedule_bootstrap_ttl_s { return shift->{schedule_bootstrap_ttl_s}; }
sub schedule_lock_ttl_s { return shift->{schedule_lock_ttl_s}; }
sub dedup_ttl_seconds  { return shift->{dedup_ttl_seconds}; }

sub sources {
  my ($self) = @_;
  return @{$self->{sources}};
}

sub source_by_id {
  my ($self, $id) = @_;
  for my $source (@{$self->{sources}}) {
    return $source if $source->{id} eq $id;
  }

  return;
}

sub _normalise_source {
  my ($source) = @_;

  croak 'Each source entry must be a hash' unless ref $source eq 'HASH';
  croak 'Source id is required' unless $source->{id};
  croak "Source '$source->{id}' is missing parser_class" unless $source->{parser_class};
  croak "Source '$source->{id}' is missing cadence_seconds"
    unless defined $source->{cadence_seconds};
  croak "Source '$source->{id}' cadence_seconds must be a positive integer"
    unless $source->{cadence_seconds} =~ /\A\d+\z/ && $source->{cadence_seconds} > 0;

  return {
    %{$source},
    id                => "$source->{id}",
    parser_class      => "$source->{parser_class}",
    cadence_seconds   => int($source->{cadence_seconds}),
    request_limit     => int($source->{request_limit} // 10),
    initial_delay_seconds => int($source->{initial_delay_seconds} // 0),
    tags              => _coerce_to_array($source->{tags}),
    query             => $source->{query} && ref $source->{query} eq 'HASH'
      ? { %{$source->{query}} }
      : {},
  };
}

sub _coerce_to_array {
  my ($value) = @_;

  return [] unless defined $value;

  if (ref $value eq 'ARRAY') {
    return [map { "$_" } @{$value}];
  }

  return ["$value"] unless ref $value;

  croak 'tags must be a scalar or an array reference';
}

sub _env_truthy {
  my ($value) = @_;
  return $value =~ /\A(?:1|true|yes|on)\z/i ? 1 : 0;
}

1;
