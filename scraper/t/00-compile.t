#!/usr/bin/env perl

use strict;
use warnings;
use Test::More;

my @modules = qw(
  Scraper::Config
  Scraper::Dedup
  Scraper::Job
  Scraper::Normalise
  Scraper::Publisher
  Scraper::Source::Base
  Scraper::Source::Confaz
  Scraper::Source::Dou
  Scraper::Source::RfbInstrucoes
);

use_ok($_) for @modules;

my $exit = system($^X, '-Ilib', '-c', 'bin/scraper.pl');
is($exit, 0, 'bin/scraper.pl compiles');

done_testing();
