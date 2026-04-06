FROM perl:5.38-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    cpanminus \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY cpanfile .
RUN cpanm --notest --installdeps .

COPY . .

CMD ["perl", "bin/scraper.pl", "minion", "worker", "-j", "4"]
