<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Production Environment Guide

## Overview

The production environment is the live environment serving real users with real data and real payments. This guide covers deployment, configuration, monitoring, and maintenance of the production FireCalc system.

## ⚠️ CRITICAL PRODUCTION CONSIDERATIONS

- **Real Money**: Production uses GoCardless LIVE environment - real payments from real customers
- **Real Data**: Customer information, payment details, invoices are all live
- **Zero Downtime**: Production must maintain high availability (target: 99.9%+)
- **Security First**: All credentials must be production-grade, encrypted, and secured
- **Compliance**: Must comply with GDPR, PCI DSS, and payment regulations

## Quick Start

### 1. Environment Setup

The production environment configuration is located at:
- ✅ Configuration directory: `configs/prod/`
- ✅ Database directory: `databases/prod/`
- ✅ UI environment file: `modules/ui/.env.production`

### 2. Configure Production

**CRITICAL**: Replace ALL placeholder values with actual production credentials:

```bash
# Payments configuration
configs/prod/payments/payments-config.conf
configs/prod/payments/gocardless-config.conf  # LIVE credentials only!
configs/prod/payments/email-config.conf

# Invoices configuration
configs/prod/invoices/company-invoice.yaml

# UI configuration
modules/ui/.env.production
```

### 3. Running in Production

**On Production Server**:
```bash
# Build backend
sbt "payments/assembly"

# Deploy and run with production environment
FIRECALC_ENV=prod java -jar firecalc-payments-assembly.jar

# Or using system property
java -Dfirecalc.env=prod -jar firecalc-payments-assembly.jar
```

**UI Deployment**:
```bash
cd modules/ui
npm run build:production

# Deploy dist/ to production web server
```

## Configuration Details

### Database Configuration

**File**: `configs/prod/payments/payments-config.conf`

Key settings:
```hocon
production {
  database {
    filename = "firecalc-payments-prod.db"
    path = "databases/prod/firecalc-payments-prod.db"
  }
  
  invoice {
    number-prefix = "FCALC-[YYYY]-"  # Clean production prefix
    number-digits = 4
    counter-starting-number = 1
  }
}
```

**Database Location**: `databases/prod/firecalc-payments-prod.db`
- **CRITICAL**: Must be backed up regularly (daily minimum)
- Contains live customer and payment data
- Requires strict access controls

### GoCardless Configuration

**File**: `configs/prod/payments/gocardless-config.conf`

**⚠️ CRITICAL**: Production MUST use GoCardless **LIVE** environment:

```hocon
production {
  # Use environment variables for security
  access-token = ${?GOCARDLESS_ACCESS_TOKEN}
  base-url = "https://api.gocardless.com"
  environment = "live"
  webhook-secret = ${?GOCARDLESS_WEBHOOK_SECRET}
  
  redirect-uri = "https://firecalc.afpma.pro/v1/payment_complete"
  exit-uri = "https://firecalc.afpma.pro/v1/payment_cancelled"
}
```

**NEVER**:
- ❌ Use sandbox credentials in production
- ❌ Commit credentials to git
- ❌ Share API keys via insecure channels

**Setup GoCardless Live**:
1. Create live account: https://manage.gocardless.com/
2. Complete business verification
3. Generate live API token
4. Configure webhook endpoint with HTTPS
5. Test with small transactions first

### Email Configuration

**File**: `configs/prod/payments/email-config.conf`

**CRITICAL**: Use production SMTP service:

```hocon
production {
  smtp {
    # Example: SendGrid
    host = "smtp.sendgrid.net"
    port = 587
    username = "apikey"
    password = ${?SENDGRID_API_KEY}
    use-tls = true
  }
  
  from {
    address = "noreply@firecalc.afpma.pro"
    name = "FireCalc"
  }
}
```

**Recommended Production Email Services**:
- **SendGrid**: Reliable, affordable, good deliverability
- **AWS SES**: Scalable, cost-effective for high volume
- **Mailgun**: Good for transactional emails

### UI Configuration

**File**: `modules/ui/.env.production`

```env
VITE_BACKEND_PROTOCOL=https
VITE_BACKEND_HOST=firecalc.afpma.pro
VITE_BACKEND_PORT=443
VITE_BACKEND_BASE_PATH=/v1
```

**Requirements**:
- ✅ MUST use HTTPS (never HTTP)
- ✅ Valid SSL certificate
- ✅ Proper DNS configuration
- ✅ CDN recommended for static assets

## Environment Comparison

| Aspect | Development | Staging | **Production** |
|--------|-------------|---------|----------------|
| **Purpose** | Development | Testing | **Live Users** |
| **Database** | dev.db | staging.db | **prod.db** |
| **Invoice Prefix** | FCALC-DEV- | FCALC-STG- | **FCALC-** |
| **GoCardless** | Sandbox | Sandbox | **LIVE** ⚠️ |
| **Email** | Mock | Test Service | **Production SMTP** |
| **Domain** | localhost | staging.domain | **firecalc.afpma.pro** |
| **Data** | Test | Realistic test | **Real** |
| **Uptime SLA** | N/A | N/A | **99.9%+** |
| **Backups** | Optional | Recommended | **Daily (Critical)** |

## Deployment

### Pre-Deployment Checklist

- [ ] All tests passing in staging
- [ ] User acceptance testing completed
- [ ] Performance testing completed
- [ ] Security audit completed
- [ ] Backup plan in place
- [ ] Rollback plan tested
- [ ] Monitoring configured
- [ ] Alerts configured
- [ ] Team notified of deployment
- [ ] Maintenance window scheduled (if needed)

### Deployment Steps

#### 1. Build Application

```bash
# Backend
sbt "payments/assembly"

# Verify JAR created
ls -lh modules/payments/target/scala-*/firecalc-payments-assembly.jar

# UI
cd modules/ui
npm run build:production

# Verify build
ls -lh dist/
```

#### 2. Deploy to Production Server

```bash
# Stop current production instance (if running)
ssh prod-server "systemctl stop firecalc-backend"

# Backup current version
ssh prod-server "cp /opt/firecalc/firecalc-payments-assembly.jar \
                     /opt/firecalc/backups/firecalc-payments-assembly-$(date +%Y%m%d).jar"

# Deploy new JAR
scp modules/payments/target/scala-*/firecalc-payments-assembly.jar \
    prod-server:/opt/firecalc/

# Deploy configs (if changed - use secure transfer!)
# NEVER deploy with placeholder credentials!
scp -r configs/prod/* prod-server:/opt/firecalc/configs/prod/

# Start new version
ssh prod-server "systemctl start firecalc-backend"

# Verify startup
ssh prod-server "systemctl status firecalc-backend"
ssh prod-server "tail -f /var/log/firecalc/backend.log"
```

#### 3. Deploy UI

```bash
# Deploy to web server
rsync -avz --delete dist/ prod-server:/var/www/firecalc.afpma.pro/

# Verify deployment
curl -I https://firecalc.afpma.pro
```

#### 4. Post-Deployment Verification

```bash
# Health check
curl https://firecalc.afpma.pro/v1/health

# Test critical endpoints
curl https://firecalc.afpma.pro/v1/products

# Monitor logs
ssh prod-server "tail -f /var/log/firecalc/backend.log"

# Check metrics
# (Access your monitoring dashboard)
```

### Rollback Procedure

If deployment fails:

```bash
# Stop new version
ssh prod-server "systemctl stop firecalc-backend"

# Restore previous version
ssh prod-server "cp /opt/firecalc/backups/firecalc-payments-assembly-YYYYMMDD.jar \
                     /opt/firecalc/firecalc-payments-assembly.jar"

# Start previous version
ssh prod-server "systemctl start firecalc-backend"

# Verify
ssh prod-server "systemctl status firecalc-backend"
```

## Monitoring & Maintenance

### Health Checks

**Automated Monitoring** (every 1 minute):
- Backend health endpoint: `/v1/health`
- Database connectivity
- External service connectivity (GoCardless, Email)
- Response times
- Error rates

**Tools**:
- Uptime monitoring: UptimeRobot, Pingdom
- Application monitoring: New Relic, Datadog
- Log aggregation: ELK Stack, Papertrail

### Database Backups

**Daily Backups** (CRITICAL):
```bash
#!/bin/bash
# /opt/firecalc/scripts/backup-db.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/opt/firecalc/backups/db"
DB_FILE="/opt/firecalc/databases/prod/firecalc-payments-prod.db"

# Create backup
sqlite3 $DB_FILE ".backup $BACKUP_DIR/firecalc-payments-prod-$DATE.db"

# Compress
gzip $BACKUP_DIR/firecalc-payments-prod-$DATE.db

# Upload to S3 (or other cloud storage)
aws s3 cp $BACKUP_DIR/firecalc-payments-prod-$DATE.db.gz \
    s3://firecalc-backups/databases/

# Clean old local backups (keep 7 days)
find $BACKUP_DIR -name "*.db.gz" -mtime +7 -delete
```

**Cron Configuration**:
```cron
# Daily at 2 AM
0 2 * * * /opt/firecalc/scripts/backup-db.sh
```

### Log Rotation

```bash
# /etc/logrotate.d/firecalc
/var/log/firecalc/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 firecalc firecalc
    sharedscripts
    postrotate
        systemctl reload firecalc-backend > /dev/null 2>&1 || true
    endscript
}
```

### Performance Monitoring

**Key Metrics**:
- Request latency (p50, p95, p99)
- Throughput (requests/second)
- Error rate (%)
- Database query time
- External API latency (GoCardless, Email)
- Memory usage
- CPU usage
- Disk I/O

**Alerts** (critical):
- Error rate > 1%
- Response time > 2 seconds (p95)
- Database unavailable
- Disk > 80% full
- Memory > 90% used

## Security

### Security Checklist

- [ ] All credentials stored securely (environment variables or secret manager)
- [ ] SSL/TLS certificates valid and auto-renewing
- [ ] Firewall configured (only necessary ports open)
- [ ] Database file permissions restricted (600)
- [ ] Application runs as non-root user
- [ ] Regular security updates applied
- [ ] Vulnerability scanning enabled
- [ ] Access logs monitored
- [ ] Failed login attempts monitored
- [ ] GDPR compliance verified
- [ ] PCI DSS compliance for payment data

### Access Control

**Production Server Access**:
- SSH key-based authentication only (no passwords)
- 2FA required for all admin accounts
- Principle of least privilege
- Regular access audit
- Immediate revocation for departed team members

**Database Access**:
- Read-only access for reporting
- Write access only for application
- No direct production database access for developers
- All queries logged

### Incident Response

**Severity Levels**:
1. **P1 (Critical)**: Service down, data loss, security breach
   - Response: Immediate (< 15 minutes)
   - Escalation: CTO, CEO notified immediately

2. **P2 (High)**: Degraded performance, partial outage
   - Response: < 1 hour
   - Escalation: Team lead notified

3. **P3 (Medium)**: Minor issues, workaround available
   - Response: < 4 hours
   - Escalation: Standard process

4. **P4 (Low)**: Cosmetic issues, no user impact
   - Response: Next business day
   - Escalation: None

**Incident Response Plan**:
1. Detect and acknowledge
2. Assess severity
3. Communicate to stakeholders
4. Investigate and diagnose
5. Implement fix or workaround
6. Verify resolution
7. Post-mortem (for P1/P2)

## Troubleshooting

### Common Issues

#### Service Won't Start

```bash
# Check logs
tail -f /var/log/firecalc/backend.log

# Check systemd status
systemctl status firecalc-backend

# Check port availability
netstat -tulpn | grep 8181

# Verify configuration
FIRECALC_ENV=prod java -jar firecalc-payments-assembly.jar --check-config
```

#### Database Locked

```bash
# Check for other processes
lsof /opt/firecalc/databases/prod/firecalc-payments-prod.db

# Check database integrity
sqlite3 /opt/firecalc/databases/prod/firecalc-payments-prod.db "PRAGMA integrity_check;"
```

#### GoCardless API Errors

```bash
# Verify credentials
echo $GOCARDLESS_ACCESS_TOKEN

# Check GoCardless status
curl https://status.gocardless.com/

# Test API connectivity
curl -H "Authorization: Bearer $GOCARDLESS_ACCESS_TOKEN" \
     https://api.gocardless.com/customers
```

#### High Memory Usage

```bash
# Check JVM memory
jstat -gc <pid>

# Adjust JVM options if needed
java -Xmx2G -Xms1G -jar firecalc-payments-assembly.jar
```

## Disaster Recovery

### Backup Strategy

**What to Backup**:
1. Database (`databases/prod/firecalc-payments-prod.db`)
2. Configuration files (`configs/prod/`)
3. Application logs (last 30 days)
4. Generated invoices/reports

**Where to Backup**:
- Primary: Cloud storage (AWS S3, Google Cloud Storage)
- Secondary: Off-site server
- Tertiary: Local encrypted drive

**Recovery Time Objective (RTO)**: 4 hours
**Recovery Point Objective (RPO)**: 24 hours

### Recovery Procedure

**Full System Recovery**:
1. Provision new server
2. Install dependencies (Java, SQLite)
3. Restore latest database backup
4. Restore configuration files
5. Deploy latest application version
6. Verify all services operational
7. Update DNS if needed
8. Notify users of service restoration

## Best Practices

1. **Never Skip Staging**: Always test in staging first
2. **Deploy During Low Traffic**: Schedule deployments for off-peak hours
3. **Monitor Closely Post-Deployment**: Watch metrics for 24 hours after deployment
4. **Communicate Proactively**: Inform users of maintenance windows
5. **Document Everything**: Keep runbooks updated
6. **Regular Drills**: Practice disaster recovery procedures
7. **Security First**: Regular security audits and updates
8. **Measure Everything**: If you can't measure it, you can't improve it

## Compliance

### GDPR Requirements

- Right to access: Customers can request their data
- Right to erasure: Customers can request data deletion
- Data portability: Provide data in machine-readable format
- Breach notification: Report breaches within 72 hours
- Privacy by design: Security built into system
- Data Protection Officer: Contact information available

### PCI DSS (if storing card data)

- ⚠️ Note: GoCardless handles card data, reducing PCI scope
- Still maintain secure environment for payment references
- Regular security assessments
- Access control and monitoring

## Support

**Production Issues**:
1. Check monitoring dashboards
2. Review application logs
3. Consult this guide
4. Escalate per incident response plan

**Emergency Contacts**:
- On-call engineer: [Phone]
- Team lead: [Phone]
- CTO: [Phone]
- GoCardless support: https://gocardless.com/support/

##Further Reading

- `STAGING.md` - Staging environment guide
- `configs/README.md` - Configuration setup
- `modules/payments/ARCHITECTURE.md` - System architecture
- GoCardless documentation: https://developer.gocardless.com/
