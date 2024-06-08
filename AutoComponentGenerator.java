package org.proyecto.fastdeliveryp_v1.generator;

import com.squareup.javapoet.*;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AutoComponentGenerator {

    //Set the name of the outputDir in the properties
    @Value("${generator.outputDir}")
    private String outputDir;

    //Configure the name of the BASE_PACKAGE in the properties
    @Value("${generator.basePackage}")
    private String BASE_PACKAGE;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    /**
     * This method is executed when the application is fully initialized and ready to receive requests.
     * Using @EventListener(ApplicationReadyEvent.class) ensures that all beans, including the EntityManager,
     * are fully initialized and available. This avoids issues that may occur with @PostConstruct,
     * which may be executed before the entire application context is fully set up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void run() {

        Set<EntityType<?>> entities = entityManagerFactory.getMetamodel().getEntities();
        List<Class<?>> entityClasses = entities.stream()
                .map(EntityType::getJavaType)
                .collect(Collectors.toList());

        if (entityClasses.isEmpty()) {
            System.out.println("No entities found.");
        } else {
            try {
                for (Class<?> entityClass : entityClasses) {
                    createDto(entityClass);
                    createMapper(entityClass);
                    createRepository(entityClass);
                    createService(entityClass);
                    createRestController(entityClass);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Generates a DTO class for the given entity, omitting fields that represent relationships.
     *
     * @param entityClass the entity class to generate a DTO for
     * @throws IOException if an I/O error occurs
     */

    private void createDto(Class<?> entityClass) throws IOException {
        String packageName = BASE_PACKAGE + ".dto";
        String className = entityClass.getSimpleName() + "Dto";
        Path outputPath = Paths.get(outputDir, packageName.replace('.', '/'), className + ".java");

        createDirectoryIfNotExists(outputPath.getParent());

        if (Files.exists(outputPath)) {
            System.out.println(className + " already exists. Skipping generation.");
            return;
        }

        TypeSpec.Builder dtoBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"));

        for (Field field : entityClass.getDeclaredFields()) {
            if (!isRelation(field)) {
                dtoBuilder.addField(field.getType(), field.getName(), Modifier.PRIVATE);
            }
        }

        JavaFile javaFile = JavaFile.builder(packageName, dtoBuilder.build())
                .build();

        javaFile.writeTo(Paths.get(outputDir));
    }

    /**
     * Generates a Mapper interface for the given entity, handling the transformation between entity and DTO.
     *
     * @param entityClass the entity class to generate a Mapper for
     * @throws IOException if an I/O error occurs
     */

    private void createMapper(Class<?> entityClass) throws IOException {
        String packageName = BASE_PACKAGE + ".mapper";
        String className = entityClass.getSimpleName() + "Mapper";
        Path outputPath = Paths.get(outputDir, packageName.replace('.', '/'), className + ".java");

        createDirectoryIfNotExists(outputPath.getParent());

        if (Files.exists(outputPath)) {
            System.out.println(className + " already exists. Skipping generation.");
            return;
        }

        TypeSpec.Builder mapperClassBuilder = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        MethodSpec toDtoMethod = MethodSpec.methodBuilder("toDto")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"))
                .addParameter(entityClass, "entity")
                .build();

        MethodSpec toEntityMethod = MethodSpec.methodBuilder("toEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(entityClass)
                .addParameter(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"), "dto")
                .build();

        mapperClassBuilder.addMethod(toDtoMethod);
        mapperClassBuilder.addMethod(toEntityMethod);

        JavaFile javaFile = JavaFile.builder(packageName, mapperClassBuilder.build())
                .build();

        javaFile.writeTo(Paths.get(outputDir));
    }


    private void createRepository(Class<?> entityClass) throws IOException {
        String packageName = BASE_PACKAGE + ".repository";
        String className = entityClass.getSimpleName() + "Repository";
        Path outputPath = Paths.get(outputDir, packageName.replace('.', '/'), className + ".java");

        createDirectoryIfNotExists(outputPath.getParent());

        if (Files.exists(outputPath)) {
            System.out.println(className + " already exists. Skipping generation.");
            return;
        }

        TypeSpec repositoryClass = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get("org.springframework.data.jpa.repository", "JpaRepository"),
                        ClassName.get(entityClass),
                        ClassName.get(Long.class)))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, repositoryClass)
                .build();

        javaFile.writeTo(Paths.get(outputDir));
    }

    /**
     * Generates a Service class for the given entity, handling CRUD operations.
     * The ID type is dynamically determined based on the entity's ID field.
     *
     * @param entityClass the entity class to generate a Service for
     * @throws IOException if an I/O error occurs
     */
    private void createService(Class<?> entityClass) throws IOException {
        String packageName = BASE_PACKAGE + ".service";
        String className = entityClass.getSimpleName() + "Service";
        Path outputPath = Paths.get(outputDir, packageName.replace('.', '/'), className + ".java");

        createDirectoryIfNotExists(outputPath.getParent());

        if (Files.exists(outputPath)) {
            System.out.println(className + " already exists. Skipping generation.");
            return;
        }

        // Get ID type from the entity class
        Class<?> idType = Long.class; // default value
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idType = field.getType();
                break;
            }
        }

        TypeSpec serviceClass = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addField(ClassName.get(BASE_PACKAGE + ".repository", entityClass.getSimpleName() + "Repository"), "repository", Modifier.PRIVATE)
                .addField(ClassName.get(BASE_PACKAGE + ".mapper", entityClass.getSimpleName() + "Mapper"), "mapper", Modifier.PRIVATE)
                .addAnnotation(Service.class)
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto")))
                        .addStatement("return repository.findAll().stream().map(mapper::toDto).collect(java.util.stream.Collectors.toList())")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findById")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"))
                        .addParameter(idType, "id")
                        .addStatement("return repository.findById(id).map(mapper::toDto).orElse(null)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("save")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"))
                        .addParameter(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"), "dto")
                        .addStatement("return mapper.toDto(repository.save(mapper.toEntity(dto)))")
                        .build())
                .addMethod(MethodSpec.methodBuilder("deleteById")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(idType, "id")
                        .addStatement("repository.deleteById(id)")
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, serviceClass)
                .build();

        javaFile.writeTo(Paths.get(outputDir));
    }

    /**
     * Generates a REST Controller class for the given entity, handling RESTful endpoints.
     * The ID type is dynamically determined based on the entity's ID field.
     *
     * @param entityClass the entity class to generate a REST Controller for
     * @throws IOException if an I/O error occurs
     */

    private void createRestController(Class<?> entityClass) throws IOException {
        String packageName = BASE_PACKAGE + ".restcontroller";
        String className = entityClass.getSimpleName() + "RestController";
        Path outputPath = Paths.get(outputDir, packageName.replace('.', '/'), className + ".java");

        createDirectoryIfNotExists(outputPath.getParent());

        if (Files.exists(outputPath)) {
            System.out.println(className + " already exists. Skipping generation.");
            return;
        }
        // get id value
        Class<?> idType = Long.class; // default value
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idType = field.getType();
                break;
            }
        }

        TypeSpec restControllerClass = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addField(ClassName.get(BASE_PACKAGE + ".service", entityClass.getSimpleName() + "Service"), "service", Modifier.PRIVATE)
                .addAnnotation(RestController.class)
                .addAnnotation(AnnotationSpec.builder(RequestMapping.class)
                        .addMember("value", "$S", "/api/" + entityClass.getSimpleName().toLowerCase() + "s")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(GetMapping.class)
                        .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto")))
                        .addStatement("return service.findAll()")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findById")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(AnnotationSpec.builder(GetMapping.class)
                                .addMember("value", "$S", "/{id}")
                                .build())
                        .returns(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"))
                        .addParameter(idType, "id")
                        .addStatement("return service.findById(id)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("save")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(PostMapping.class)
                        .returns(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"))
                        .addParameter(ClassName.get(BASE_PACKAGE + ".dto", entityClass.getSimpleName() + "Dto"), "dto")
                        .addStatement("return service.save(dto)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("deleteById")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(AnnotationSpec.builder(DeleteMapping.class)
                                .addMember("value", "$S", "/{id}")
                                .build())
                        .addParameter(idType, "id")
                        .addStatement("service.deleteById(id)")
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, restControllerClass)
                .build();

        javaFile.writeTo(Paths.get(outputDir));
    }


    private void createDirectoryIfNotExists(Path directoryPath) {
        if (!Files.exists(directoryPath)) {
            try {
                Files.createDirectories(directoryPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Checks if the given field represents a JPA relationship.
     *
     * @param field the field to check
     * @return true if the field is annotated with a JPA relationship annotation, false otherwise
     */
    private boolean isRelation(Field field) {
        return field.isAnnotationPresent(OneToOne.class) ||
                field.isAnnotationPresent(OneToMany.class) ||
                field.isAnnotationPresent(ManyToOne.class) ||
                field.isAnnotationPresent(ManyToMany.class);
    }
}