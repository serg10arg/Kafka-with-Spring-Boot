# Kafka con Docker para Desarrollo Local

Este repositorio proporciona la configuración y los comandos necesarios para levantar un entorno de Apache Kafka localmente usando Docker y Docker Compose. Incluye configuraciones para un bróker único y para un clúster de múltiples brókeres, ideal para desarrollo y pruebas de aplicaciones, como las basadas en Spring Boot.

## Requisitos Previos

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/) (generalmente incluido con Docker Desktop)

---

## 🚀 Inicio Rápido

### 1. Levantar un Bróker Único de Kafka

Este comando iniciará un contenedor de Zookeeper y un contenedor para un único bróker de Kafka.

```bash
docker-compose up -d
```

### 2. Levantar un Clúster de 3 Brókeres de Kafka

Para un entorno más realista que simule un clúster, utiliza el archivo de multi-bróker.

```bash
docker-compose -f docker-compose-multi-broker.yml up -d
```

> **Nota:** Usa el flag `-d` para ejecutar los contenedores en segundo plano (detached mode).

---

## 🧠 Entendiendo la Configuración de Red en Docker

Configurar la red de Kafka en Docker puede ser complicado. La clave está en cómo los brókeres anuncian sus direcciones a los clientes (productores/consumidores) y a otros brókeres.

### Configuración Clave en `docker-compose.yml`

```yaml
services:
  kafka1:
    image: confluentinc/cp-kafka:7.3.2
    hostname: kafka1
    ports:
      - "9092:9092" # Para clientes externos (tu máquina host)
      - "29092:29092" # Para clientes en otros contenedores Docker
    environment:
      # Anuncia las diferentes formas de conectarse al bróker
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka1:19092,EXTERNAL://${DOCKER_HOST_IP:-127.0.0.1}:9092,DOCKER://host.docker.internal:29092
      # Mapea los nombres de los listeners a protocolos de seguridad
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,DOCKER:PLAINTEXT
      # Define qué listener usar para la comunicación entre brókeres
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      # ... otras variables
```

### Explicación de las Variables de Entorno

- `KAFKA_ADVERTISED_LISTENERS`: Esta es la variable **más importante**. Le dice a Kafka qué direcciones debe devolver a los clientes para que puedan conectarse.
    - `INTERNAL://kafka1:19092`: Para comunicación **dentro de la red de Docker Compose**. Otros servicios en el mismo `docker-compose.yml` (como otros brókeres) usarán esta dirección.
    - `EXTERNAL://${DOCKER_HOST_IP:-127.0.0.1}:9092`: Para clientes **fuera de Docker**, como tu aplicación Spring Boot ejecutándose en tu máquina local. Utiliza la IP de tu máquina y el puerto `9092`.
    - `DOCKER://host.docker.internal:29092`: Un caso especial para clientes que se ejecutan en **otros contenedores Docker** pero no en la misma red. `host.docker.internal` es un DNS especial que resuelve a la IP del host.

- `KAFKA_INTER_BROKER_LISTENER_NAME`: Especifica cuál de los listeners anteriores deben usar los brókeres para comunicarse entre sí. Al establecerlo en `INTERNAL`, aseguramos que el tráfico del clúster permanezca dentro de la red optimizada de Docker.

> Para una explicación más profunda, consulta este excelente artículo: Kafka Listeners - Explained.

---

## 🛠️ Comandos Básicos de Kafka (CLI)

Para ejecutar los siguientes comandos, primero necesitas acceder al shell de uno de los contenedores de Kafka.

```bash
# Acceder al contenedor kafka1
docker exec -it kafka1 bash
```

Una vez dentro, puedes usar las herramientas de línea de comandos de Kafka.

**Nota Importante:** Dentro del contenedor, siempre nos conectaremos al bróker usando su listener `INTERNAL` (`kafka1:19092`), ya que es la comunicación dentro de la red de Docker.

### 1. Crear un Tópico

```bash
# Para un solo bróker
kafka-topics --bootstrap-server kafka1:19092 \
             --create \
             --topic test-topic \
             --partitions 1 --replication-factor 1

# Para un clúster de 3 brókeres (mayor durabilidad)
kafka-topics --bootstrap-server kafka1:19092 \
             --create \
             --topic test-topic-cluster \
             --partitions 3 --replication-factor 3
```

### 2. Producir Mensajes

#### Mensajes Simples

```bash
kafka-console-producer --bootstrap-server kafka1:19092 \
                       --topic test-topic
> Escribe tu primer mensaje
> Y otro más
> (Ctrl+C para salir)
```

#### Mensajes con Clave y Valor

```bash
kafka-console-producer --bootstrap-server kafka1:19092 \
                       --topic test-topic \
                       --property "parse.key=true" \
                       --property "key.separator=:"
> clave1:valor1
> clave2:valor2
> (Ctrl+C para salir)
```

### 3. Consumir Mensajes

#### Consumo Básico

```bash
kafka-console-consumer --bootstrap-server kafka1:19092 \
                       --topic test-topic \
                       --from-beginning
```

#### Consumir Clave, Valor y Metadatos

```bash
kafka-console-consumer --bootstrap-server kafka1:19092 \
                       --topic test-topic \
                       --from-beginning \
                       --property "print.key=true" \
                       --property "key.separator= : " \
                       --property "print.timestamp=true" \
                       --property "print.headers=true"
```

---

## ⚙️ Comandos Avanzados y de Administración

Estos comandos se pueden ejecutar directamente desde tu terminal sin necesidad de entrar al contenedor.

### 1. Listar todos los Tópicos

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:19092 --list
```

### 2. Describir un Tópico

Muestra información detallada como el líder de cada partición, las réplicas y las réplicas sincronizadas (ISR).

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:19092 --describe --topic test-topic
```

### 3. Alterar un Tópico

Por ejemplo, para aumentar el número de particiones.

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:19092 --alter --topic test-topic --partitions 5
```

### 4. Administrar Grupos de Consumidores

#### Listar Grupos de Consumidores

```bash
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:19092 --list
```

#### Describir un Grupo de Consumidores

Muestra el offset actual, el final del log y el lag para cada partición que el grupo está consumiendo.

```bash
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:19092 --describe --group mi-grupo-consumidor
```

### 5. Configurar `min.insync.replicas`

Esta configuración garantiza una mayor durabilidad, ya que el productor esperará la confirmación de un número mínimo de réplicas.

```bash
docker exec kafka1 kafka-configs --bootstrap-server kafka1:19092 \
  --entity-type topics --entity-name test-topic-cluster \
  --alter --add-config min.insync.replicas=2
```

---

## 🔍 Inspección y Logs

### Ubicación de Archivos dentro del Contenedor

Puedes inspeccionar los archivos de configuración y los logs de datos directamente en el contenedor.

```bash
docker exec -it kafka1 bash

# Archivo de configuración del servidor
cat /etc/kafka/server.properties

# Directorio de datos y logs de los tópicos
ls -l /var/lib/kafka/data/
```

### Ver el Contenido de un Segmento de Log

Permite ver los mensajes tal como están almacenados en el disco del bróker.

```bash
docker exec kafka1 kafka-run-class kafka.tools.DumpLogSegments \
  --deep-iteration \
  --print-data \
  --files /var/lib/kafka/data/test-topic-0/00000000000000000000.log
```

---

## 🧹 Limpieza

Para detener y eliminar todos los contenedores, redes y volúmenes creados, ejecuta el comando `down` correspondiente al archivo que usaste para levantar el entorno.

```bash
# Para la configuración de un solo bróker
docker-compose down

# Para la configuración de multi-bróker
docker-compose -f docker-compose-multi-broker.yml down
```