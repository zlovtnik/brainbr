#!/usr/bin/env perl

use strict;
use warnings;
use File::Temp qw(tempdir);
use Test::Exception;
use Test::More;

use lib 't/lib';

use Scraper::Config;

my $dir = tempdir(CLEANUP => 1);
my $yaml = "$dir/sources.yaml";

{
  open my $fh, '>', $yaml or die "Cannot write $yaml: $!";
  print {$fh} <<'YAML';
sources:
  - id: scalar-tags
    parser_class: Scraper::Source::Confaz
    cadence_seconds: 3600
    tags: confaz
YAML
  close $fh;
}

local $ENV{REDIS_URL} = 'redis://localhost:6379/0';
local $ENV{SCRAPER_SOURCES_FILE} = $yaml;

my $config = Scraper::Config->from_env(root => $dir);
my $source = $config->source_by_id('scalar-tags');
is_deeply($source->{tags}, ['confaz'], 'scalar tags are coerced to a one-element array');

{
  open my $fh, '>', $yaml or die "Cannot write $yaml: $!";
  print {$fh} <<'YAML';
sources:
  - id: bad-cadence
    parser_class: Scraper::Source::Confaz
    cadence_seconds: 0
YAML
  close $fh;
}

dies_ok { Scraper::Config->from_env(root => $dir) } 'zero cadence_seconds is rejected';

done_testing();
