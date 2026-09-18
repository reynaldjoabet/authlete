# Common tasks. Requires sbt 2.x on PATH (see project/build.properties for the pinned version).
#
# Everything goes through `sbt --client`, which reuses the running sbt server instead of paying JVM
# startup per invocation. The first call in a session starts that server, so it is slow; the rest
# are not.

SHELL := /bin/bash

.PHONY: help build compile test check format fmt-check run clean verify jar image image-run

IMAGE ?= authlete-as:latest

help:
	@echo "Authlete Authorization Server"
	@echo ""
	@echo "  make build      Compile main sources"
	@echo "  make test       Run the test suite"
	@echo "  make format     Rewrite sources with scalafmt"
	@echo "  make fmt-check  Fail if anything is unformatted"
	@echo "  make check      compile + fmt-check + test (what CI runs)"
	@echo "  make run        Run the server locally (requires .env)"
	@echo "  make clean      Drop build output and sbt's caches"
	@echo ""
	@echo "  make jar        Build the self-contained jar (target/authlete.jar)"
	@echo "  make image      Build the container image ($(IMAGE))"
	@echo "  make image-run  Run that image locally (requires .env)"

build compile:
	sbt --client "root/compile"

test:
	sbt --client "root/test"

format:
	sbt --client "root/scalafmt"

fmt-check:
	sbt --client "root/scalafmtCheck"

# The gate. Each step is its own recipe line, so a failure stops the target rather than being
# swallowed -- the previous version piped compiler output into `grep ... || echo`, whose exit status
# is zero whether or not anything matched, making the whole check incapable of failing.
#
# There is no separate lint step because there is nothing left for one to do: scalacOptions already
# carries -Werror with -Xlint:all, so any warning fails `compile` outright.
check verify: fmt-check compile test
	@echo "check: OK"

# `runMain Main` rather than `run`: several classes under src/main/scala carry a main method (the
# crypto examples), so a bare `run` stops to ask which one and fails when nothing is on stdin.
#
# `set -a` exports each assignment as it is read. Note this *sources* .env, so it is shell syntax:
# any value containing a space has to be quoted there ("15 seconds"), which .env.example already
# does. The older `export $$(cat .env | xargs)` form silently truncated such a value to its first
# word, which meant a duration reached the config parser as a bare number.
run:
	@if [ ! -f .env ]; then \
		echo "No .env found. Copy .env.example and fill in the required values."; \
		exit 1; \
	fi
	set -a && . ./.env && set +a && sbt --client "root/runMain Main"

# The same artifact the image's build stage produces, so the container's entrypoint can be
# reproduced locally with `java -jar target/authlete.jar`.
jar:
	sbt --client "root/assembly"

# No dependency on `stage`: the image builds its own inside the build stage, from a clean context.
# Depending on it here would only slow the target down and invite a stale local directory to be
# mistaken for what the image contains.
image:
	docker build -t $(IMAGE) .

image-run: image
	@if [ ! -f .env ]; then \
		echo "No .env found. Copy .env.example and fill in the required values."; \
		exit 1; \
	fi
	docker run --rm -it --env-file .env -p 8080:8080 $(IMAGE)

# cleanFull, not clean: plain `clean` leaves sbt's own caches in place, so a stale cache can survive
# it and produce output that does not match the sources.
clean:
	sbt --client "cleanFull"
