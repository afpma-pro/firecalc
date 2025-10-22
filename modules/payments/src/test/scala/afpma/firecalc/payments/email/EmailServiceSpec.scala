/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import afpma.firecalc.payments.config.ConfigLoader

/**
 * Test suite for EmailService using dev environment configuration
 * 
 * IMPORTANT: Before running these tests, you must configure your dev SMTP credentials
 * in configs/dev/payments/email-config.conf
 * 
 * Recommended setup for testing:
 * - Use Mailtrap.io (free account) for catching test emails
 * - Or use MailHog local SMTP server: docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog
 * 
 * These tests will send actual emails to the configured SMTP server.
 */
class EmailServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  given Logger[IO] = Slf4jLogger.getLogger[IO]
  
  val DEFAULT_RECIPIENT_EMAIL_FOR_TEST = "logiciel+test@afpma.pro"

  "EmailService.sendEmail" should {

    "send email with no attachments" in {
      val result = for {
        // Load email config from dev environment
        emailConfig <- ConfigLoader.loadEmailConfig[IO]()
        
        // Create email service
        emailService <- EmailService.create[IO](emailConfig)
        
        // Create test email with no attachments
        message = EmailMessage(
          to = EmailAddress.unsafeFromString(DEFAULT_RECIPIENT_EMAIL_FOR_TEST),
          subject = EmailSubject("Test Email - No Attachments"),
          content = EmailContent(
            """
            |<html>
            |<body>
            |  <h2>Test Email</h2>
            |  <p>This is a test email with <strong>no attachments</strong>.</p>
            |  <p>Sent from EmailService automated test suite.</p>
            |</body>
            |</html>
            """.stripMargin
          ),
          attachments = List.empty
        )
        
        // Send email
        result <- emailService.sendEmail(message)
        
      } yield result

      result.asserting { emailResult =>
        emailResult shouldBe a[EmailResult]
        emailResult match {
          case EmailSent => 
            info("Email sent successfully")
            succeed
          case EmailFailed(error) =>
            fail(s"Email sending failed: $error")
        }
      }
    }

    "send email with one PDF attachment" in {
      // Create fake PDF content
      val pdfContent = "Fake PDF content for testing".getBytes("UTF-8")
      
      val result = for {
        // Load email config from dev environment
        emailConfig <- ConfigLoader.loadEmailConfig[IO]()
        
        // Create email service
        emailService <- EmailService.create[IO](emailConfig)
        
        // Create test email with one attachment
        message = EmailMessage(
          to = EmailAddress.unsafeFromString(DEFAULT_RECIPIENT_EMAIL_FOR_TEST),
          subject = EmailSubject("Test Email - One PDF Attachment"),
          content = EmailContent(
            """
            |<html>
            |<body>
            |  <h2>Test Email</h2>
            |  <p>This is a test email with <strong>one PDF attachment</strong>.</p>
            |  <p>Please find the attached PDF document.</p>
            |  <p>Sent from EmailService automated test suite.</p>
            |</body>
            |</html>
            """.stripMargin
          ),
          attachments = List(
            EmailAttachment(
              filename = "test-document.pdf",
              contentType = "application/pdf",
              content = pdfContent
            )
          )
        )
        
        // Send email
        result <- emailService.sendEmail(message)
        
      } yield result

      result.asserting { emailResult =>
        emailResult shouldBe a[EmailResult]
        emailResult match {
          case EmailSent => 
            info("Email with one attachment sent successfully")
            succeed
          case EmailFailed(error) =>
            fail(s"Email sending failed: $error")
        }
      }
    }

    "send email with two PDF attachments" in {
      // Create fake PDF content for attachments
      val pdfContent1 = "Fake PDF content for invoice".getBytes("UTF-8")
      val pdfContent2 = "Fake PDF content for report".getBytes("UTF-8")
      
      val result = for {
        // Load email config from dev environment
        emailConfig <- ConfigLoader.loadEmailConfig[IO]()
        
        // Create email service
        emailService <- EmailService.create[IO](emailConfig)
        
        // Create test email with two attachments
        message = EmailMessage(
          to = EmailAddress.unsafeFromString(DEFAULT_RECIPIENT_EMAIL_FOR_TEST),
          subject = EmailSubject("Test Email - Two PDF Attachments"),
          content = EmailContent(
            """
            |<html>
            |<body>
            |  <h2>Test Email</h2>
            |  <p>This is a test email with <strong>two PDF attachments</strong>.</p>
            |  <p>Please find the attached documents:</p>
            |  <ul>
            |    <li>Invoice PDF</li>
            |    <li>Report PDF</li>
            |  </ul>
            |  <p>Sent from EmailService automated test suite.</p>
            |</body>
            |</html>
            """.stripMargin
          ),
          attachments = List(
            EmailAttachment(
              filename = "invoice.pdf",
              contentType = "application/pdf",
              content = pdfContent1
            ),
            EmailAttachment(
              filename = "report.pdf",
              contentType = "application/pdf",
              content = pdfContent2
            )
          )
        )
        
        // Send email
        result <- emailService.sendEmail(message)
        
      } yield result

      result.asserting { emailResult =>
        emailResult shouldBe a[EmailResult]
        emailResult match {
          case EmailSent => 
            info("Email with two attachments sent successfully")
            succeed
          case EmailFailed(error) =>
            fail(s"Email sending failed: $error")
        }
      }
    }

    "handle email sending failures gracefully" in {
      val result = for {
        // Load email config but use invalid SMTP settings
        emailConfig <- ConfigLoader.loadEmailConfig[IO]()
        
        // Create email config with invalid host to force failure
        invalidConfig = emailConfig.copy(smtpHost = "invalid.smtp.host.test")
        
        // Create email service with invalid config
        emailService <- EmailService.create[IO](invalidConfig)
        
        // Create test email
        message = EmailMessage(
          to = EmailAddress.unsafeFromString(DEFAULT_RECIPIENT_EMAIL_FOR_TEST),
          subject = EmailSubject("Test Email - Should Fail"),
          content = EmailContent("<html><body><p>This should fail</p></body></html>"),
          attachments = List.empty
        )
        
        // Attempt to send email (should fail)
        result <- emailService.sendEmail(message)
        
      } yield result

      result.asserting { emailResult =>
        emailResult shouldBe a[EmailFailed]
        emailResult match {
          case EmailSent => 
            fail("Email should have failed with invalid SMTP configuration")
          case EmailFailed(error) =>
            info(s"Email correctly failed with error: $error")
            error should not be empty
            succeed
        }
      }
    }
  }

  "EmailService configuration loading" should {

    "successfully load email config from dev environment" in {
      val result = ConfigLoader.loadEmailConfig[IO]()

      result.asserting { config =>
        config.smtpHost should not be empty
        config.smtpPort should be > 0
        config.smtpPort should be < 65536
        config.username should not be empty
        config.password should not be empty
        config.fromAddress.value should not be empty
        config.fromName should not be empty
        info(s"Loaded config: ${config.smtpHost}:${config.smtpPort} from ${config.fromAddress.value}")
        succeed
      }
    }
  }
}
