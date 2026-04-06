#!/usr/bin/env perl

use strict;
use warnings;
use utf8;
use Test::More;

use Scraper::Normalise qw(clean_text extract_first_date normalise_law_ref parse_flexible_date);

is(
  normalise_law_ref("  Convênio   ICMS  42/2026 \n"),
  'CONVENIO ICMS 42/2026',
  'law_ref strips accents, uppercases, and collapses whitespace',
);

is(
  normalise_law_ref('Instrução Normativa RFB nº 123'),
  'INSTRUCAO NORMATIVA RFB N 123',
  'law_ref normalization is deterministic for accented titles',
);

is(
  clean_text("Linha 1\r\n\r\nLinha&nbsp;2"),
  "Linha 1\n\nLinha 2",
  'text cleaning normalizes line endings and HTML entities',
);

is(parse_flexible_date('2026-04-01'), '2026-04-01', 'ISO date is preserved');
is(parse_flexible_date('01/04/2026'), '2026-04-01', 'BR date format is normalized');
is(parse_flexible_date('1 de abril de 2026'), '2026-04-01', 'Portuguese month names are parsed');
is(extract_first_date('Publicado em Brasília, 1 de abril de 2026.'), '2026-04-01', 'date can be extracted from body text');

done_testing();
