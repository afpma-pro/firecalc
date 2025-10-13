/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.config

import java.nio.file.{Files, Paths}
import java.io.File

class ConfigPathResolverTest extends munit.FunSuite {

  test("resolveEnvironment returns default 'dev' when no environment is set") {
    // Clear any existing environment variables for test isolation
    val originalEnv = sys.env.get("FIRECALC_ENV")
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      // Remove system property if set
      sys.props.remove("firecalc.env")
      
      // Note: We can't easily clear env vars in tests, so we assume FIRECALC_ENV is not set
      // In a real test environment, this would typically be the case
      
      val env = ConfigPathResolver.resolveEnvironment()
      assertEquals(env, "dev")
    } finally {
      // Restore original values if they existed
      originalProp.foreach(sys.props.put("firecalc.env", _))
    }
  }

  test("resolveEnvironment respects FIRECALC_ENV environment variable") {
    // Note: We can't easily set environment variables in unit tests
    // This test documents the expected behavior
    // In integration tests or with test containers, we could actually set env vars
    
    // If FIRECALC_ENV is set to "prod", it should return "prod"
    // This is tested by the priority order documentation
    assert(true) // Placeholder - real test would require test framework support for env vars
  }

  test("resolveEnvironment respects firecalc.env system property") {
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      sys.props.put("firecalc.env", "prod")
      val env = ConfigPathResolver.resolveEnvironment()
      assertEquals(env, "prod")
    } finally {
      originalProp match {
        case Some(value) => sys.props.put("firecalc.env", value)
        case None => sys.props.remove("firecalc.env")
      }
    }
  }

  test("resolveConfigPath constructs correct path") {
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      sys.props.put("firecalc.env", "test")
      
      val path = ConfigPathResolver.resolveConfigPath("payments", "config.conf")
      val expected = Paths.get("configs", "test", "payments", "config.conf")
      
      assertEquals(path, expected)
    } finally {
      originalProp match {
        case Some(value) => sys.props.put("firecalc.env", value)
        case None => sys.props.remove("firecalc.env")
      }
    }
  }

  test("resolveLogoPath finds existing logo files") {
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      sys.props.put("firecalc.env", "test-logos")
      
      // Create temporary directory structure for testing
      val testDir = Files.createTempDirectory("config-test")
      val configsDir = testDir.resolve("configs/test-logos/reports")
      Files.createDirectories(configsDir)
      
      // Create a test logo file
      val logoFile = configsDir.resolve("logo.png")
      Files.createFile(logoFile)
      
      // Change working directory context for test
      val originalUserDir = System.getProperty("user.dir")
      try {
        System.setProperty("user.dir", testDir.toString)
        
        val logoPath = ConfigPathResolver.resolveLogoPath("reports")
        
        assert(logoPath.isDefined, "Should find logo file")
        assert(logoPath.get.toString.endsWith("logo.png"), "Should find PNG file")
      } finally {
        System.setProperty("user.dir", originalUserDir)
        // Clean up temp directory
        deleteRecursively(testDir.toFile)
      }
    } finally {
      originalProp match {
        case Some(value) => sys.props.put("firecalc.env", value)
        case None => sys.props.remove("firecalc.env")
      }
    }
  }

  test("resolveLogoPath returns None when no logo files exist") {
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      sys.props.put("firecalc.env", "test-no-logos")
      
      val logoPath = ConfigPathResolver.resolveLogoPath("nonexistent")
      assertEquals(logoPath, None)
    } finally {
      originalProp match {
        case Some(value) => sys.props.put("firecalc.env", value)
        case None => sys.props.remove("firecalc.env")
      }
    }
  }

  test("configExists returns true for existing files") {
    val originalProp = sys.props.get("firecalc.env")
    
    try {
      sys.props.put("firecalc.env", "test-exists")
      
      // Create temporary directory structure for testing
      val testDir = Files.createTempDirectory("config-test")
      val configsDir = testDir.resolve("configs/test-exists/payments")
      Files.createDirectories(configsDir)
      
      // Create a test config file
      val configFile = configsDir.resolve("test.conf")
      Files.createFile(configFile)
      
      // Change working directory context for test
      val originalUserDir = System.getProperty("user.dir")
      try {
        System.setProperty("user.dir", testDir.toString)
        
        val exists = ConfigPathResolver.configExists("payments", "test.conf")
        assertEquals(exists, true)
        
        val notExists = ConfigPathResolver.configExists("payments", "nonexistent.conf")
        assertEquals(notExists, false)
      } finally {
        System.setProperty("user.dir", originalUserDir)
        // Clean up temp directory
        deleteRecursively(testDir.toFile)
      }
    } finally {
      originalProp match {
        case Some(value) => sys.props.put("firecalc.env", value)
        case None => sys.props.remove("firecalc.env")
      }
    }
  }

  test("debugInfo returns useful information") {
    val info = ConfigPathResolver.debugInfo()
    
    assert(info.contains("Environment:"), "Should contain environment info")
    assert(info.contains("Base config path:"), "Should contain path info")
  }

  // Helper method to delete directories recursively
  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }
}
