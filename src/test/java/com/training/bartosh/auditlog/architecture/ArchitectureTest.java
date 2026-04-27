package com.training.bartosh.auditlog.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.training.bartosh.auditlog")
class ArchitectureTest {

  @ArchTest
  static final ArchRule domainHasNoSpringDependencies =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..")
          .because("agents.md: domain/ is pure Java — no Spring imports");

  @ArchTest
  static final ArchRule domainHasNoJpaDependencies =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
          .because("agents.md: domain/ is pure Java — no JPA / Hibernate imports");

  @ArchTest
  static final ArchRule controllerDoesNotAccessPersistence =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..persistence..")
          .because(
              "agents.md: controller/ never reaches into persistence/ directly — go through service/");

  @ArchTest
  static final ArchRule layeredDependencies =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Controller")
          .definedBy("..controller..")
          .layer("Service")
          .definedBy("..service..")
          .layer("Persistence")
          .definedBy("..persistence..")
          .layer("Domain")
          .definedBy("..domain..")
          .whereLayer("Controller")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Controller")
          .whereLayer("Persistence")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Controller", "Service", "Persistence");
}
