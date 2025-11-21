# Sistema de Eventos de Biblioteca con Kafka y Spring Boot

## Introducción

Este proyecto es una implementación de una arquitectura orientada a eventos utilizando Java, Spring Boot y Apache Kafka. Consiste en dos microservicios principales: un productor que expone una API REST para publicar eventos de biblioteca (creación y actualización de libros) y un consumidor que procesa estos eventos de forma asíncrona, los persiste en una base de datos y gestiona los fallos de manera resiliente.

## Características Principales

- **API RESTful**: Endpoints para la creación (`POST`) y actualización (`PUT`) de eventos de biblioteca.
- **Comunicación Asíncrona**: Utiliza Apache Kafka como bróker de mensajería para desacoplar los servicios.
- **Manejo de Errores Avanzado**: Implementa los patrones de reintentos (Retry) y cola de mensajes muertos (Dead-Letter Topic - DLT) para garantizar que no se pierdan mensajes.
- **Reprocesamiento Automático**: Incluye una tarea programada (`Scheduler`) que reintenta automáticamente los mensajes que fallaron con errores recuperables.
- **Persistencia de Datos**: El servicio consumidor utiliza Spring Data JPA para persistir los eventos procesados en una base de datos H2 en memoria.
- **Entorno Dockerizado**: Proporciona archivos `docker-compose` para levantar fácilmente un clúster de Kafka (single y multi-broker) para desarrollo y pruebas.
- **Configuración Multi-Entorno**: Utiliza perfiles de Spring (`local`, `nonprod`, `prod`, `ssl`) para gestionar diferentes configuraciones de manera limpia.
- **Cobertura de Pruebas**: Incluye pruebas unitarias y de integración para validar la funcionalidad de ambos microservicios.

## Arquitectura del Sistema

El sistema sigue una arquitectura de microservicios orientada a eventos, donde los servicios se comunican a través de un bus de mensajes centralizado (Kafka).

El flujo principal es el siguiente:
1. Un cliente envía una petición HTTP (POST o PUT) al microservicio **Productor**.
2. El **Productor** valida la petición y publica un mensaje en el tópico principal de Kafka (`library-events`).
3. El microservicio **Consumidor** está suscrito a este tópico, consume el mensaje y lo procesa.
4. La lógica de negocio en el **Consumidor** persiste la información en la base de datos.

Para el manejo de errores, el flujo es:
- Si el procesamiento falla por un error no recuperable, el mensaje se envía al tópico **DLT**.
- Si falla por un error recuperable (ej. fallo de red con la BD), se reintenta varias veces. Si sigue fallando, se envía al tópico **RETRY** y se guarda un registro del fallo en la base de datos.
- Un **Scheduler** revisa periódicamente la base de datos en busca de registros marcados para reintento y los vuelve a procesar.

![diagrama](img/01-Captura-de-pantalla-2025-11-20%20214321.png)
![diagrama2](img/02-Captura-de-pantalla-2025-11-20%20213711.png)
![diagram3](img/03-Captura-de-pantalla-2025-11-20%20213608.png)

## Tecnologías Utilizadas

- **Lenguaje**: Java 17
- **Framework**: Spring Boot 3
- **Mensajería**: Spring for Apache Kafka
- **Acceso a Datos**: Spring Data JPA
- **Base de Datos**: H2 (en memoria)
- **Infraestructura**: Docker, Docker Compose
- **Pruebas**: JUnit 5, Mockito, Spring Boot Test, EmbeddedKafka
- **Utilidades**: Lombok

## Documentación de la API

La API es expuesta por el servicio `library-events-producer`. Los endpoints principales son:

*   `POST /v1/libraryevent`
    *   **Descripción**: Crea un nuevo evento de biblioteca. El cuerpo de la petición debe ser un JSON que represente un `LibraryEvent` con el tipo `NEW`.
    *   **Respuesta Exitosa**: `201 Created` con el evento creado en el cuerpo.

*   `PUT /v1/libraryevent`
    *   **Descripción**: Actualiza un evento de biblioteca existente. El cuerpo de la petición debe ser un JSON que represente un `LibraryEvent` con el tipo `UPDATE` y un `libraryEventId` válido.
    *   **Respuesta Exitosa**: `200 OK` con el evento actualizado en el cuerpo.

Ambos endpoints realizan validaciones sobre el cuerpo de la petición. En caso de datos inválidos, se devolverá una respuesta `400 Bad Request` con un mensaje detallando los errores.