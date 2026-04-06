FROM perl:5.38-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    cpanminus \
    gcc \
    make \
    libssl-dev \
    libz-dev \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r scraper && useradd -r -g scraper -d /app -s /sbin/nologin scraper

WORKDIR /app

COPY cpanfile .
RUN cpanm --notest --installdeps .

COPY . .
RUN chown -R scraper:scraper /app

USER scraper

CMD ["perl", "bin/scraper.pl", "minion", "worker", "-j", "4"]
