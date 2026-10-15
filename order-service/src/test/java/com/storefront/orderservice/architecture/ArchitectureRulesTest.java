package com.storefront.orderservice.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.storefront.orderservice", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainModelMustNotDependOnSpringOrJpa =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                    .because("Domain entities and value objects must be pure Java records free of framework coupling");

    @ArchTest
    static final ArchRule domainModelClassesMustBeRecordsOrEnums =
            classes()
                    .that().resideInAPackage("..domain.model..")
                    .should().beRecords()
                    .orShould().beEnums()
                    .because("Domain aggregates and value objects must be immutable Java records or enums");

    @ArchTest
    static final ArchRule noFieldInjection =
            fields()
                    .should().notBeAnnotatedWith(Autowired.class)
                    .because("Constructor injection is mandatory across all components; field @Autowired is strictly prohibited");

    @ArchTest
    static final ArchRule noSetterInjection =
            methods()
                    .should().notBeAnnotatedWith(Autowired.class)
                    .because("Setter injection is prohibited; use constructor injection to enforce immutability and testability");
}
