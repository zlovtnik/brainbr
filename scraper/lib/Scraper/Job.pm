package Scraper::Job;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use Exporter qw(import);
use Mojo::JSON qw(encode_json);
use Time::Piece ();
use UUID::Tiny ':std';

use Scraper::Normalise qw(clean_text normalise_law_ref parse_flexible_date);

our @EXPORT_OK = qw(build_job encode_job nil_uuid);

use constant NIL_UUID => '00000000-0000-0000-0000-000000000000';

sub build_job {
  my (%args) = @_;

  my $source_id = $args{source_id}
    or croak 'source_id is required to build an ingestion job';
  my $law_type = $args{law_type}
    or croak 'law_type is required to build an ingestion job';

  my $law_ref = normalise_law_ref($args{law_ref} // $args{title});
  croak 'Unable to derive law_ref for ingestion job' unless length $law_ref;

  my $raw_content = clean_text($args{raw_content});
  croak 'raw_content is required to build an ingestion job' unless length $raw_content;

  my $job_id = create_uuid_as_string(UUID_V4);
  my $tag_values   = ref($args{tags})      eq 'ARRAY' ? $args{tags}      : [];
  my @tags          = _unique(grep { defined && length } @{$tag_values});
  my $ncm_scope_values = ref($args{ncm_scope}) eq 'ARRAY' ? $args{ncm_scope} : [];
  my @ncm_scope     = grep { defined && length } @{$ncm_scope_values};

  return {
    job_id       => $job_id,
    company_id   => NIL_UUID,
    law_ref      => $law_ref,
    law_type     => $law_type,
    source_url   => _nullable_scalar($args{source_url}),
    raw_content  => $raw_content,
    published_at => parse_flexible_date($args{published_at}),
    effective_at => parse_flexible_date($args{effective_at}),
    tags         => \@tags,
    state        => _nullable_scalar($args{state}),
    ncm_scope    => \@ncm_scope,
    request_id   => "scraper-$source_id-$job_id",
    attempt      => 0,
    created_at   => Time::Piece->gmtime->datetime . 'Z',
  };
}

sub encode_job {
  my ($job) = @_;
  return encode_json($job);
}

sub nil_uuid {
  return NIL_UUID;
}

sub _nullable_scalar {
  my ($value) = @_;
  return undef unless defined $value && length $value;
  return $value;
}

sub _unique {
  my (@values) = @_;
  my %seen;
  return grep { !$seen{$_}++ } @values;
}

1;
