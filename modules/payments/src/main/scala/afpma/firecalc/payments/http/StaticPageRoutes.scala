/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.*
import org.http4s.syntax.literals.*
import org.http4s.headers.`Content-Type`
import io.circe.syntax.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.*
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.domain.Codecs.given
import afpma.firecalc.payments.email.EmailConfig
import org.typelevel.log4cats.Logger
import org.typelevel.ci.CIStringSyntax

// Import i18n translations
import afpma.firecalc.payments.i18n.implicits.given
import io.taig.babel.*

class StaticPageRoutes[F[_]: Async](emailConfig: EmailConfig)(implicit logger: Logger[F])
    extends Http4sDsl[F]:

    import BackendCompatibleLanguage.given

    private def extractLanguage(request: Request[F]): BackendCompatibleLanguage = {
        // Try to get language from query parameter first
        request.params.get("lang") match {
            case Some(code) => BackendCompatibleLanguage.fromCodeWithFallback(code)
            case _ => 
                // Fallback to Accept-Language header parsing
                request.headers.get(ci"Accept-Language") match {
                    case Some(header) if header.head.value.toLowerCase.contains("en") => BackendCompatibleLanguage.English
                    case Some(header) if header.head.value.toLowerCase.contains("fr") => BackendCompatibleLanguage.French
                    case _ => BackendCompatibleLanguage.DefaultLanguage // Default fallback
                }
        }
    }

    private def generatePaymentCompleteHtml(language: BackendCompatibleLanguage): String = {
        given BackendCompatibleLanguage = language
        val translations = I18N_Payments
        val page = translations.pages.payment_complete
        val langCode = language.code
        
        s"""<!DOCTYPE html>
<html lang="$langCode">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title}</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
            background-color: #f8f9fa;
            padding: 16px;
        }
        .container {
            text-align: center;
            max-width: 500px;
            background: white;
            padding: 40px;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            border: 1px solid #e9ecef;
        }
        .success-icon {
            width: 64px;
            height: 64px;
            background-color: #28a745;
            border-radius: 50%;
            margin: 0 auto 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            color: white;
        }
        h1 {
            color: #343a40;
            font-size: 24px;
            font-weight: 600;
            margin: 0 0 16px 0;
        }
        .description {
            color: #6c757d;
            font-size: 16px;
            line-height: 1.6;
            margin: 0 0 32px 0;
        }
        .info-box {
            background-color: #e7f3ff;
            border: 1px solid #b8daff;
            border-radius: 8px;
            padding: 16px;
            margin: 24px 0;
            text-align: left;
        }
        .info-box h3 {
            color: #004085;
            font-size: 14px;
            font-weight: 600;
            margin: 0 0 8px 0;
        }
        .info-box ul {
            color: #004085;
            font-size: 14px;
            margin: 0;
            padding-left: 16px;
        }
        .info-box li {
            margin: 4px 0;
        }
        .button {
            background-color: #007bff;
            color: white;
            border: none;
            border-radius: 6px;
            padding: 12px 24px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            transition: background-color 0.2s ease;
        }
        .button:hover {
            background-color: #0056b3;
        }
        .security-note {
            font-size: 12px;
            color: #868e96;
            margin-top: 24px;
            padding: 12px;
            background-color: #f8f9fa;
            border-radius: 6px;
        }
        @media (max-width: 480px) {
            .container {
                margin: 0;
                padding: 24px;
                border-radius: 0;
                min-height: 100vh;
                display: flex;
                flex-direction: column;
                justify-content: center;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="success-icon">✓</div>
        
        <h1>${page.heading}</h1>
        
        <p class="description">
            ${page.message}
        </p>
        
        <div class="info-box">
            <h3>${page.next_steps}</h3>
            <ul>
                <li>${page.order_info}</li>
                <li>${emailConfig.supportEmail.value}</li>
            </ul>
        </div>
        
        <button class="button" onclick="handleCloseWindow()">${page.return_home}</button>
        
        <div class="security-note">
            ${page.security_notice}
        </div>
    </div>

    <script>
        function handleCloseWindow() {
            if (window.opener) {
                window.close();
            } else {
                if (history.length > 1) {
                    history.back();
                } else {
                    window.close();
                }
            }
            
            setTimeout(() => {
                const container = document.querySelector('.container');
                if (container) {
                    container.innerHTML += `
                        <div style="margin-top: 20px; padding: 16px; background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 6px; color: #856404;">
                            ${page.unable_to_close}
                        </div>
                    `;
                }
            }, 1000);
        }

        let autoCloseTimer = setTimeout(() => {
            handleCloseWindow();
        }, 30000);

        document.addEventListener('click', () => {
            clearTimeout(autoCloseTimer);
        });

        document.addEventListener('keydown', () => {
            clearTimeout(autoCloseTimer);
        });
    </script>
</body>
</html>"""
    }

    private def generatePaymentCancelledHtml(language: BackendCompatibleLanguage): String = {
        given BackendCompatibleLanguage = language
        val translations = I18N_Payments
        val page = translations.pages.payment_cancelled
        val langCode = language.code
        
        s"""<!DOCTYPE html>
<html lang="$langCode">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title}</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
            background-color: #f8f9fa;
            padding: 16px;
        }
        .container {
            text-align: center;
            max-width: 500px;
            background: white;
            padding: 40px;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            border: 1px solid #e9ecef;
        }
        .warning-icon {
            width: 64px;
            height: 64px;
            background-color: #ffc107;
            border-radius: 50%;
            margin: 0 auto 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            color: white;
        }
        h1 {
            color: #343a40;
            font-size: 24px;
            font-weight: 600;
            margin: 0 0 16px 0;
        }
        .description {
            color: #6c757d;
            font-size: 16px;
            line-height: 1.6;
            margin: 0 0 32px 0;
        }
        .info-box {
            background-color: #fff3cd;
            border: 1px solid #ffeaa7;
            border-radius: 8px;
            padding: 16px;
            margin: 24px 0;
            text-align: left;
        }
        .info-box h3 {
            color: #856404;
            font-size: 14px;
            font-weight: 600;
            margin: 0 0 8px 0;
        }
        .info-box ul {
            color: #856404;
            font-size: 14px;
            margin: 0;
            padding-left: 16px;
        }
        .info-box li {
            margin: 4px 0;
        }
        .button {
            background-color: #007bff;
            color: white;
            border: none;
            border-radius: 6px;
            padding: 12px 24px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            transition: background-color 0.2s ease;
            margin: 0 8px;
        }
        .button:hover {
            background-color: #0056b3;
        }
        .button.secondary {
            background-color: #6c757d;
        }
        .button.secondary:hover {
            background-color: #545b62;
        }
        .contact-info {
            font-size: 14px;
            color: #6c757d;
            margin-top: 24px;
            padding: 16px;
            background-color: #f8f9fa;
            border-radius: 6px;
        }
        @media (max-width: 480px) {
            .container {
                margin: 0;
                padding: 24px;
                border-radius: 0;
                min-height: 100vh;
                display: flex;
                flex-direction: column;
                justify-content: center;
            }
            .button {
                display: block;
                margin: 8px 0;
                width: 100%;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="warning-icon">⚠</div>
        
        <h1>${page.heading}</h1>
        
        <p class="description">
            ${page.message}
        </p>
        
        <div class="info-box">
            <h3>${page.reason_info}</h3>
            <ul>
                <li>${page.retry_payment}</li>
                <li>${emailConfig.supportEmail.value}</li>
            </ul>
        </div>
        
        <div style="margin: 24px 0;">
            <button class="button secondary" onclick="handleCloseWindow()">${page.return_home}</button>
        </div>
        
        <div class="contact-info">
            ${page.need_help_contact(emailConfig.supportEmail.value)}
        </div>
    </div>

    <script>
        function handleCloseWindow() {
            if (window.opener) {
                window.close();
            } else {
                if (history.length > 1) {
                    history.back();
                } else {
                    window.close();
                }
            }
            
            setTimeout(() => {
                const container = document.querySelector('.container');
                if (container) {
                    container.innerHTML += `
                        <div style="margin-top: 20px; padding: 16px; background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 6px; color: #721c24;">
                            ${page.unable_to_close}
                        </div>
                    `;
                }
            }, 1000);
        }

        setTimeout(() => {
            handleCloseWindow();
        }, 10000);
    </script>
</body>
</html>"""
    }

    val routes_V1: HttpRoutes[F] = HttpRoutes.of[F] {

        case request @ GET -> Root / "v1" / "payment_complete" =>
            for
                _ <- logger.info("Serving payment complete page")
                language = extractLanguage(request)
                _ <- logger.info(s"Using language: ${language.code}")
                htmlContent = generatePaymentCompleteHtml(language)
                response <- Ok(htmlContent)
                    .map(_.withContentType(`Content-Type`(MediaType.text.html)))
            yield response

        case request @ GET -> Root / "v1" / "payment_cancelled" =>
            for
                _ <- logger.info("Serving payment cancelled page")
                language = extractLanguage(request)
                _ <- logger.info(s"Using language: ${language.code}")
                htmlContent = generatePaymentCancelledHtml(language)
                response <- Ok(htmlContent)
                    .map(_.withContentType(`Content-Type`(MediaType.text.html)))
            yield response
    }

object StaticPageRoutes:
    def create[F[_]: Async](emailConfig: EmailConfig)(implicit logger: Logger[F]): StaticPageRoutes[F] =
        new StaticPageRoutes[F](emailConfig)
