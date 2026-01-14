package ru.growerhub.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {
    // spisok domenov dlya facade-pravila
    private static final String[] DOMAINS = {
            "auth",
            "device",
            "firmware",
            "journal",
            "plant",
            "pump",
            "sensor",
            "user"
    };

    // klassy vneshney arhitektury dlya proverki
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ru.growerhub.backend");

    // pravilo: na kazhdyj domen dolzhen byt odin public facade
    @Test
    void eachDomainHasSingleFacade() {
        for (String domain : DOMAINS) {
            List<JavaClass> facades = CLASSES.stream()
                    .filter(it -> it.getPackageName().equals("ru.growerhub.backend." + domain))
                    .filter(it -> it.getSimpleName().endsWith("Facade"))
                    .filter(it -> it.getModifiers().contains(JavaModifier.PUBLIC))
                    .collect(Collectors.toList());
            // sosednee pravilo: dlja nahozhdeniya facade-dlya domena
            Assertions.assertEquals(1, facades.size(), "Expected exactly one public Facade in " + domain);
        }
    }

    // pravilo: v korne domena tolko *Facade ili package-info
    @Test
    void domainRootOnlyFacade() {
        for (String domain : DOMAINS) {
            List<JavaClass> rootClasses = CLASSES.stream()
                    .filter(it -> it.getPackageName().equals("ru.growerhub.backend." + domain))
                    .filter(it -> !it.isInnerClass())
                    .filter(it -> !it.getName().contains("$"))
                    .filter(it -> !it.getSimpleName().endsWith("Facade"))
                    .filter(it -> !it.getSimpleName().equals("package-info"))
                    .collect(Collectors.toList());
            Assertions.assertTrue(rootClasses.isEmpty(), "Expected only *Facade in root package of " + domain);
        }
    }

    // pravilo: engine-pakety dostupny tolko svoemu domenu
    @Test
    void engineModulesOnlyUsedByOwnDomain() {
        for (String domain : DOMAINS) {
            ArchRule isolationRule = classes()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + ".engine..")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("ru.growerhub.backend." + domain + "..")
                    .allowEmptyShould(true);
            isolationRule.check(CLASSES);
        }
    }

    // pravilo: jpa-pakety dostupny tolko svoemu domenu
    @Test
    void jpaModulesOnlyUsedByOwnDomain() {
        for (String domain : DOMAINS) {
            ArchRule isolationRule = classes()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + ".jpa..")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("ru.growerhub.backend." + domain + "..")
                    .allowEmptyShould(true);
            isolationRule.check(CLASSES);
        }
    }

    // pravilo: domen ne zavisit ot adapterov (api/mqtt)
    @Test
    void noAdapterDependencyFromDomain() {
        for (String domain : DOMAINS) {
            ArchRule apiRule = noClasses()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + "..")
                    .should().dependOnClassesThat().resideInAPackage("ru.growerhub.backend.api..");
            ArchRule mqttRule = noClasses()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + "..")
                    .should().dependOnClassesThat().resideInAPackage("ru.growerhub.backend.mqtt..");
            apiRule.allowEmptyShould(true).check(CLASSES);
            mqttRule.allowEmptyShould(true).check(CLASSES);
        }
    }

    // pravilo: domen ne zavisit ot jpa drugogo domena
    @Test
    void domainJpaExclusive() {
        for (String domain : DOMAINS) {
            List<String> otherJpaPackages = java.util.Arrays.stream(DOMAINS)
                    .filter(it -> !it.equals(domain))
                    .map(it -> "ru.growerhub.backend." + it + ".jpa..")
                    .toList();
            ArchRule jpaIsolationRule = noClasses()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(otherJpaPackages.toArray(new String[0]));
            jpaIsolationRule.allowEmptyShould(true).check(CLASSES);
        }
    }

    // pravilo: @Transactional tolko na facade
    @Test
    void facadeTransactionalOnly() {
        ArchRule classRule = classes()
                .that().areAnnotatedWith(Transactional.class)
                .should().haveSimpleNameEndingWith("Facade");
        ArchRule methodRule = methods()
                .that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().haveSimpleNameEndingWith("Facade");
        classRule.allowEmptyShould(true).check(CLASSES);
        methodRule.allowEmptyShould(true).check(CLASSES);
    }

    // pravilo: common.contract i common.util bez anotacii Spring i Entity
    @Test
    void commonContractAndUtilStayClean() {
        // net komponentov v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Component.class).check(CLASSES);
        // net servisov v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Service.class).check(CLASSES);
        // net repository v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Repository.class).check(CLASSES);
        // net konfiguracij v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Configuration.class).check(CLASSES);
        // net entity v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Entity.class).check(CLASSES);

        // net komponentov v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Component.class).check(CLASSES);
        // net servisov v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Service.class).check(CLASSES);
        // net repository v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Repository.class).check(CLASSES);
        // net konfiguracij v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Configuration.class).check(CLASSES);
        // net entity v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Entity.class).check(CLASSES);
    }

    // pravilo: engine i jpa ne zavisyat ot common.component
    @Test
    void engineAndJpaShouldNotDependOnCommonComponent() {
        ArchRule engineRule = noClasses()
                .that().resideInAPackage("ru.growerhub.backend..engine..")
                .should().dependOnClassesThat().resideInAPackage("ru.growerhub.backend.common.component..");
        ArchRule jpaRule = noClasses()
                .that().resideInAPackage("ru.growerhub.backend..jpa..")
                .should().dependOnClassesThat().resideInAPackage("ru.growerhub.backend.common.component..");
        engineRule.allowEmptyShould(true).check(CLASSES);
        jpaRule.allowEmptyShould(true).check(CLASSES);
    }

    // helper dlja pravila bez anotacij
    private static ArchRule noAnnotationRule(String packagePattern, Class<? extends Annotation> annotation) {
        return noClasses()
                .that().resideInAPackage(packagePattern)
                .should().beAnnotatedWith(annotation)
                .allowEmptyShould(true);
    }
}
