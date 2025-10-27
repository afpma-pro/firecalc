# =============================================================================
# Stage 1: Builder - Install Rust and compile Typst CLI
# =============================================================================
# This stage handles all build-time dependencies and compilation.
# We use a separate builder stage to keep the final runtime image minimal.
FROM eclipse-temurin:21-jre-jammy AS builder

# Install build dependencies
# - curl: Download rustup installer
# - pkg-config: Required for building native dependencies
# - build-essential: GCC and other build tools needed by cargo
# - libssl-dev: OpenSSL development libraries (required for typst-cli)
# - ca-certificates: SSL certificates for secure downloads
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        pkg-config \
        build-essential \
        libssl-dev \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Install Rust via rustup
# Rust is required to compile typst-cli from source
ENV RUSTUP_HOME=/usr/local/rustup \
    CARGO_HOME=/usr/local/cargo \
    PATH=/usr/local/cargo/bin:$PATH

RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --default-toolchain stable && \
    chmod -R a+w $RUSTUP_HOME $CARGO_HOME

# Install Typst CLI
# Typst is used for PDF report generation in the application
# --locked ensures we use the exact dependency versions from Cargo.lock
RUN cargo install --locked typst-cli

# Verify typst installation
RUN typst --version

# =============================================================================
# Stage 2: Runtime - Final production image
# =============================================================================
# This stage contains only what's needed to run the application.
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install runtime dependencies
# - sqlite3: Database engine for local data storage
# - ca-certificates: Required for HTTPS connections (e.g., payment APIs)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        sqlite3 \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create a non-root user for running the application
# Running as non-root improves security by limiting potential damage from vulnerabilities
RUN groupadd -r appuser && \
    useradd -r -g appuser -d /app -s /sbin/nologin -c "Application user" appuser

# Set working directory
WORKDIR /app

# Copy Typst binary from builder stage
# Only the compiled binary is needed, not the entire Rust toolchain
COPY --from=builder /usr/local/cargo/bin/typst /usr/local/bin/typst

# Copy the pre-built JAR file from build context
# This assumes the JAR has been built outside the Docker build process
COPY modules/payments/target/scala-3.7.3/firecalc-payments-assembly.jar /app/firecalc-payments-assembly.jar

# Copy logos for invoice and report generation (Typst requires files on filesystem)
# The logos are copied from modules/*/configs/*.jpg which were placed there during the build
# This ensures Typst can find the logos at their expected paths relative to /app
RUN mkdir -p /app/modules/invoices/configs /app/modules/reports/src/main/resources
COPY modules/invoices/configs/logo.png /app/modules/invoices/configs/logo.png
COPY modules/reports/src/main/resources/logo.jpg /app/modules/reports/src/main/resources/logo.jpg

# Create mount points for configuration and data persistence
# These directories are intended to be mounted as volumes at runtime:
# - /app/configs: Application configuration files (YAML, CONF)
# - /app/databases: SQLite database files
RUN mkdir -p /app/configs /app/databases && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose application port
# The payments backend service listens on port 8181
EXPOSE 8181

# Set default environment variable
# FIRECALC_ENV determines which configuration to load
# Can be overridden at runtime: docker run -e FIRECALC_ENV=production ...
ENV FIRECALC_ENV=staging

# Health check to verify the application is running
# Adjust the endpoint based on your application's health check endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8181/health || exit 1

# Application entry point
# Runs the assembled JAR file with the JVM
ENTRYPOINT ["java", "-jar", "/app/firecalc-payments-assembly.jar"]