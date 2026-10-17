# Debian 13 base pinned by digest; dependency source revisions are pinned in CMake.
FROM debian:trixie-slim@sha256:a29215f6a35e51e22adffa17f89e9d2ef06214e64a2bad10d765c46aea49f11f AS toolchain
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates cmake g++ git ninja-build libssl-dev libsqlite3-dev python3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src

FROM toolchain AS build
COPY CMakeLists.txt ./
COPY core/ core/
COPY desktop/ desktop/
COPY shared/ shared/
COPY docs/RUNNING.md docs/RUNNING.md
COPY scripts/smoke.py scripts/smoke.py
ARG BUILD_JOBS=2
RUN --mount=type=secret,id=build_ca,target=/run/secrets/build-ca.crt \
    if [ -f /run/secrets/build-ca.crt ]; then export GIT_SSL_CAINFO=/run/secrets/build-ca.crt; fi \
    && cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DIR_BUILD_DESKTOP=OFF \
       -DCMAKE_TLS_CAINFO="${GIT_SSL_CAINFO:-/etc/ssl/certs/ca-certificates.crt}" \
    && cmake --build build --parallel "$BUILD_JOBS" \
    && ctest --test-dir build --output-on-failure \
    && python3 scripts/smoke.py build/investment-radar \
    && cmake --install build --prefix /out --component Runtime
CMD ["python3", "scripts/smoke.py", "build/investment-radar"]

# Export with docker build --target artifacts --output type=local,dest=dist/linux .
FROM scratch AS artifacts
COPY --from=build /out/ /
