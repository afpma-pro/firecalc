/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n.utils

import scala.jdk.CollectionConverters.*

import cats.Id
import cats.syntax.all.*

import io.taig.babel.*
import org.ekrich.config.Config
import org.ekrich.config.ConfigFactory

final class CustomLoader(configs: Map[String, String]) extends Loader[Id]:
  override def load(base: String, locales: Set[Locale]): Id[Translations[Babel]] = 
    val all = locales.toList
      .map { locale =>
        val configContent = configs.getOrElse(
          locale.printLanguageTag,
          ""
        )
          
        (locale, ConfigFactory.parseString(configContent))
      }
      .map { case (locale, config) => 
          toBabel(config).map(Translation(locale, _)) 
      }
      .map {
        case Left(e) => throw e
        case Right(value) => value
      }
      
    Translations.from(all)


  def toBabel(config: Config): Either[Throwable, Babel] =
    config.root.entrySet.asScala
      .map(entry => entry.getKey -> entry.getValue)
      .toList
      .foldLeftM(Map.empty[String, Babel]) { case (result, (key, value)) =>
        toBabel(value.unwrapped, Path.one(key)).map(babel => result + (key -> babel))
      }
      .map(Babel.Object.apply)

  def toBabel(value: AnyRef, path: Path): Either[Throwable, Babel] = value match {
    case value: String => Babel.Value(value).asRight
    case obj: java.util.Map[_, _] =>
      obj.asScala.toList
        .traverse { case (a, b) =>
          val key = a.asInstanceOf[String]
          val value = b.asInstanceOf[AnyRef]
          toBabel(value, path / key).tupleLeft(key)
        }
        .map(Babel.from)
    case null => Right(Babel.Null)
    case value =>
      val message = s"Unsupported type: ${value.getClass.getSimpleName} ${path.printPlaceholder}"
      Left(new IllegalArgumentException(message))
  }

  

end CustomLoader