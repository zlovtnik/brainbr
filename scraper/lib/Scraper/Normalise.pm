package Scraper::Normalise;

use strict;
use warnings;
use utf8;
use v5.30;

use Encode qw(decode FB_CROAK);
use Exporter qw(import);
use Mojo::DOM;
use Mojo::Util qw(html_unescape);
use Text::Unidecode qw(unidecode);

our @EXPORT_OK = qw(
  clean_text
  extract_first_date
  extract_text_from_html
  normalise_law_ref
  parse_flexible_date
);

my %MONTH_NUMBER = (
  janeiro   => 1,
  fevereiro => 2,
  marco     => 3,
  abril     => 4,
  maio      => 5,
  junho     => 6,
  julho     => 7,
  agosto    => 8,
  setembro  => 9,
  outubro   => 10,
  novembro  => 11,
  dezembro  => 12,
);

sub normalise_law_ref {
  my ($value) = @_;
  return q{} unless defined $value;

  $value = clean_text($value);
  $value =~ s/\b[nN]\s*[º°](?=\s|$)/N/g;
  $value = uc unidecode($value);
  $value =~ s/[^A-Z0-9\/(). -]+/ /g;
  $value =~ s/\s+/ /g;
  $value =~ s/\A\s+|\s+\z//g;

  $value = substr($value, 0, 255) if length($value) > 255;

  return $value;
}

sub clean_text {
  my ($value) = @_;
  return q{} unless defined $value;

  $value = _decode_text($value);
  $value = html_unescape($value);
  $value =~ s/\r\n?/\n/g;
  $value =~ s/\x{00A0}/ /g;
  $value =~ s/[ \t\f]+/ /g;
  $value =~ s/ *\n */\n/g;
  $value =~ s/\n{3,}/\n\n/g;
  $value =~ s/\A\s+|\s+\z//g;

  return $value;
}

sub extract_text_from_html {
  my ($html) = @_;
  return q{} unless defined $html && length $html;

  my $dom = Mojo::DOM->new($html);
  return clean_text($dom->all_text);
}

sub extract_first_date {
  my ($value) = @_;
  return undef unless defined $value && length $value;

  if ($value =~ /(\d{4}-\d{2}-\d{2})/) {
    return parse_flexible_date($1);
  }

  if ($value =~ /(\d{1,2}[\/.-]\d{1,2}[\/.-]\d{4})/) {
    return parse_flexible_date($1);
  }

  if ($value =~ /(\d{1,2}(?:[ºo])?\s+de\s+[[:alpha:]]+\s+de\s+\d{4})/i) {
    return parse_flexible_date($1);
  }

  return undef;
}

sub parse_flexible_date {
  my ($value) = @_;
  return undef unless defined $value && length $value;

  $value = clean_text($value);

  if ($value =~ /\A(\d{4})[-\/](\d{2})[-\/](\d{2})\z/) {
    my ($y, $m, $d) = (int $1, int $2, int $3);
    return undef unless _valid_date($y, $m, $d);
    return sprintf '%04d-%02d-%02d', $y, $m, $d;
  }

  if ($value =~ /\A(\d{1,2})[\/.-](\d{1,2})[\/.-](\d{4})\z/) {
    my ($d, $m, $y) = (int $1, int $2, int $3);
    return undef unless _valid_date($y, $m, $d);
    return sprintf '%04d-%02d-%02d', $y, $m, $d;
  }

  my $ascii = lc unidecode($value);
  if ($ascii =~ /\A(\d{1,2})(?:[ºo])?\s+de\s+([a-z]+)\s+de\s+(\d{4})\z/) {
    my ($day, $month_name, $year) = (int $1, $2, int $3);
    my $month = $MONTH_NUMBER{$month_name} or return undef;
    return undef unless _valid_date($year, $month, $day);
    return sprintf '%04d-%02d-%02d', $year, $month, $day;
  }

  return undef;
}

sub _valid_date {
  my ($y, $m, $d) = @_;
  return 0 unless $m >= 1 && $m <= 12 && $d >= 1;
  my @days_in_month = (0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
  my $leap = ($y % 4 == 0 && ($y % 100 != 0 || $y % 400 == 0)) ? 1 : 0;
  $days_in_month[2] = 29 if $leap;
  return $d <= $days_in_month[$m];
}

sub _decode_text {
  my ($value) = @_;
  return $value if utf8::is_utf8($value);

  my $decoded = eval { decode('UTF-8', $value, FB_CROAK) };
  return defined $decoded ? $decoded : $value;
}

1;
