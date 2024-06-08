# AutoComponentGenerator
Basic model of auto generator of rest, mapper, dto, service and repository

## Configuration

Remember to set the following properties in your application configuration file:

```properties
generator.outputDir=src/main/java
generator.basePackage=org.proyecto.baco

You need to replace these values with the path to your project and your package structure accordingly.

## Example of Usage in Spring Boot Application

To use the AutoComponentGenerator in your Spring Boot application, you can configure it as a bean and set the properties accordingly.

```
