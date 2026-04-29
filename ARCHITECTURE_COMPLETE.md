# 🏗️ Vue d'Ensemble Complète de l'Architecture gRPC Microservices

## 📊 Architecture Globale

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT (REST API)                               │
│                                                                           │
│  POST /api/orchestrator/execute-saga?orderId=ORDER_001&amount=50.0       │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  OrderOrchestratorController (REST)    │
        │  Port: 9091 (HTTP)                     │
        │  Receives REST request                 │
        └────────────────────┬───────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │    OrderOrchestrator (@Service)       │
        │    Implements Saga Pattern             │
        │    Uses gRPC Stubs to call services    │
        └─┬──────────────────┬──────────────────┬┘
          │                  │                  │
  ┌───────▼────┐     ┌───────▼────┐    ┌──────▼─────┐
  │   gRPC     │     │   gRPC     │    │   gRPC     │
  │ Channel to │     │ Channel to │    │ Channel to │
  │Order Svc   │     │Kitchen Svc │    │Acctg Svc   │
  │ :9091      │     │ :9092      │    │ :9093      │
  └───────┬────┘     └───────┬────┘    └──────┬─────┘
          │                  │                  │
          ▼                  ▼                  ▼
  ┌─────────────┐     ┌──────────────┐    ┌──────────────┐
  │   Order     │     │   Kitchen    │    │ Accounting   │
  │  Service    │     │   Service    │    │  Service     │
  │   Impl      │     │   Impl       │    │   Impl       │
  │  @GrpcSvr   │     │  @GrpcSvr    │    │  @GrpcSvr    │
  │ Port: 9091  │     │ Port: 9092   │    │ Port: 9093   │
  └──────┬──────┘     └──────┬───────┘    └──────┬───────┘
         │                   │                   │
         ▼                   ▼                   ▼
  ┌─────────────┐     ┌──────────────┐    ┌──────────────┐
  │   Order     │     │   Dish       │    │  Invoice     │
  │  Repository │     │  Repository  │    │ Repository   │
  │  (JPA)      │     │   (JPA)      │    │  (JPA)       │
  └──────┬──────┘     └──────┬───────┘    └──────┬───────┘
         │                   │                   │
         ▼                   ▼                   ▼
  ┌─────────────┐     ┌──────────────┐    ┌──────────────┐
  │PostgreSQL   │     │PostgreSQL    │    │PostgreSQL    │
  │order_db     │     │kitchen_db    │    │accounting_db │
  │Port: 5431   │     │Port: 5432    │    │Port: 5433    │
  └─────────────┘     └──────────────┘    └──────────────┘
```

---

## 1️⃣ FICHIERS PROTOBUF (.proto) - Le Contrat de Communication

### 🎯 Rôle des Fichiers Protobuf

Les fichiers `.proto` définissent le **contrat de communication** entre les microservices via gRPC. Ils spécifient:
- **Les Services** : Les RPC (Remote Procedure Calls) disponibles
- **Les Messages** : Les structures de données pour les requêtes et réponses
- Le **Package** : Le namespace des classes générées (ex: `com.gourmet.order`)

### 📝 Fichier: `src/main/proto/order.proto`

```protobuf
syntax = "proto3";

option java_multiple_files = true;
package com.gourmet.order;

service OrderService {
  rpc UpdateStatus(UpdateStatusRequest) returns (UpdateStatusResponse);
}

message UpdateStatusRequest {
  string orderId = 1;        // Numéro unique de la commande
  string status = 2;         // Nouveau statut (APPROVAL_PENDING, APPROVED, REJECTED)
}

message UpdateStatusResponse {
  bool acknowledged = 1;     // Confirmation de la mise à jour
}
```

**🔑 Points Clés:**
- `service OrderService` : Définit le service gRPC disponible
- `rpc UpdateStatus()` : Méthode RPC (synchrone)
- `message` : Structure de données (contrats)
- Le compilateur protobuf génère: `OrderServiceGrpc` (classe de stubification)

---

### 📝 Fichier: `src/main/proto/kitchen.proto`

```protobuf
syntax = "proto3";

option java_multiple_files = true;
package com.gourmet.kitchen;

service KitchenService {
  rpc CreateTicket(TicketRequest) returns (TicketResponse);
  rpc RejectTicket(RejectRequest) returns (RejectResponse); // Compensation
}

message TicketRequest {
  string orderId = 1;
}

message TicketResponse {
  bool success = 1;
}

message RejectRequest {
  string orderId = 1;
}

message RejectResponse {
  bool acknowledged = 1;
}
```

**🔑 Points Clés:**
- `RejectTicket()` : Utilisé pour la **compensation** en cas d'échec du Saga
- Deux méthodes pour le flux normal et le rollback

---

### 📝 Fichier: `src/main/proto/Accounting.proto`

```protobuf
syntax = "proto3";

option java_multiple_files = true;
package com.gourmet.accounting;

service AccountingService {
  rpc AuthorizeCard(AuthorizeRequest) returns (AuthorizeResponse);
}

message AuthorizeRequest {
  string orderId = 1;
  double amount = 2;    // Montant à autoriser
}

message AuthorizeResponse {
  bool authorized = 1;  // true si montant < 100, false sinon
}
```

**🔑 Points Clés:**
- Logique métier: `authorized = (amount < 100.0)`
- Si `false` → déclenche la **compensation Saga**

---

## 2️⃣ IMPLÉMENTATIONS DE SERVICES - @GrpcService

### 🎯 Rôle des Implémentations

Chaque classe `ServiceImpl` **implémente les méthodes RPC** définies dans les fichiers `.proto`. L'annotation `@GrpcService` enregistre automatiquement le service auprès du serveur gRPC Spring Boot.

### 📝 Fichier: `src/main/java/org/example/order/OrderServiceImpl.java`

```java
package org.example.order;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService  // ✅ Spring Boot gRPC Server découvre et enregistre ce service
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    @Override
    public void updateStatus(UpdateStatusRequest request, StreamObserver<UpdateStatusResponse> responseObserver) {
        // 1. Extraction des paramètres de la requête
        String orderId = request.getOrderId();
        String status = request.getStatus();
        
        System.out.println("[OrderService] Mise à jour de la commande " + orderId + " au statut : " + status);

        // 2. Construction de la réponse
        UpdateStatusResponse response = UpdateStatusResponse.newBuilder()
                .setAcknowledged(true)  // Succès
                .build();

        // 3. Envoi de la réponse au client
        responseObserver.onNext(response);
        responseObserver.onCompleted();  // Signal de fin
    }
}
```

**🔑 Points Clés:**
- `@GrpcService` : Annotation Spring Boot gRPC (version 3.1.0)
- `extends OrderServiceGrpc.OrderServiceImplBase` : Héritage du contrat proto
- `StreamObserver<T>` : Pattern asynchrone pour envoyer la réponse
- `responseObserver.onNext()` : Envoie les données
- `responseObserver.onCompleted()` : Signal de fin du stream

---

### 📝 Fichier: `src/main/java/org/example/kitchen/KitchenServiceImpl.java`

```java
package org.example.kitchen;

import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.RejectResponse;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class KitchenServiceImpl extends KitchenServiceGrpc.KitchenServiceImplBase {
    
    @Override
    public void createTicket(TicketRequest request, StreamObserver<TicketResponse> responseObserver) {
        System.out.println("[KitchenService] Création du ticket pour la commande : " + request.getOrderId());

        TicketResponse response = TicketResponse.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void rejectTicket(RejectRequest request, StreamObserver<RejectResponse> responseObserver) {
        // 🔄 COMPENSATION : Annule le ticket en cas d'échec du Saga
        System.out.println("[KitchenService] COMPENSATION : Annulation du ticket pour la commande : " + request.getOrderId());

        RejectResponse response = RejectResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

**🔑 Points Clés:**
- `createTicket()` : Crée un ticket de cuisine
- `rejectTicket()` : **Compensation** - annule le ticket si nécessaire

---

### 📝 Fichier: `src/main/java/org/example/accounting/AccountingServiceImpl.java`

```java
package org.example.accounting;

import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AccountingServiceImpl extends AccountingServiceGrpc.AccountingServiceImplBase {
    
    @Override
    public void authorizeCard(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
        double amount = request.getAmount();
        // ⚠️ Logique métier : autoriser seulement si montant < 100
        boolean isAuthorized = amount < 100.0;

        System.out.println("[AccountingService] Demande d'autorisation pour la commande " + request.getOrderId() + " d'un montant de " + amount);
        System.out.println("[AccountingService] Résultat de l'autorisation : " + (isAuthorized ? "ACCEPTEE" : "REFUSEE"));

        AuthorizeResponse response = AuthorizeResponse.newBuilder()
                .setAuthorized(isAuthorized)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

**🔑 Points Clés:**
- Logique métier: `isAuthorized = (amount < 100.0)`
- Si `false` → Le Saga lancera la **compensation**

---

## 3️⃣ SAGA ORCHESTRATOR - Le Chef d'Orchestre

### 🎯 Rôle du Saga Orchestrator

L'orchestrator implémente le **Saga Pattern** pour une transaction distribuée. Il:
1. Appelle les services dans un ordre précis
2. En cas d'échec, déclenche la **compensation** (rollback)
3. Gère les canaux gRPC et les stubs

### 📝 Fichier: `src/main/java/org/example/orchestrator/OrderOrchestrator.java`

```java
package org.example.orchestrator;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service  // ✅ Enregistré comme service Spring
public class OrderOrchestrator {

    // 📍 Adresses des microservices gRPC
    private static final String ORDER_SERVICE_HOST = "localhost";
    private static final int ORDER_SERVICE_PORT = 9091;

    private static final String KITCHEN_SERVICE_HOST = "localhost";
    private static final int KITCHEN_SERVICE_PORT = 9092;

    private static final String ACCOUNTING_SERVICE_HOST = "localhost";
    private static final int ACCOUNTING_SERVICE_PORT = 9093;

    // Canaux gRPC (connexions)
    private ManagedChannel orderChannel;
    private ManagedChannel kitchenChannel;
    private ManagedChannel accountingChannel;

    // Stubs bloquants (clients gRPC synchrones)
    private OrderServiceGrpc.OrderServiceBlockingStub orderStub;
    private KitchenServiceGrpc.KitchenServiceBlockingStub kitchenStub;
    private AccountingServiceGrpc.AccountingServiceBlockingStub accountingStub;

    public OrderOrchestrator() {
        initializeChannels();
    }

    private void initializeChannels() {
        // Créer les canaux gRPC (comme des connexions TCP)
        this.orderChannel = ManagedChannelBuilder
                .forAddress(ORDER_SERVICE_HOST, ORDER_SERVICE_PORT)
                .usePlaintext()  // Pas de SSL/TLS pour cet exemple
                .build();

        this.kitchenChannel = ManagedChannelBuilder
                .forAddress(KITCHEN_SERVICE_HOST, KITCHEN_SERVICE_PORT)
                .usePlaintext()
                .build();

        this.accountingChannel = ManagedChannelBuilder
                .forAddress(ACCOUNTING_SERVICE_HOST, ACCOUNTING_SERVICE_PORT)
                .usePlaintext()
                .build();

        // Créer les stubs (clients qui font les appels RPC)
        this.orderStub = OrderServiceGrpc.newBlockingStub(orderChannel);
        this.kitchenStub = KitchenServiceGrpc.newBlockingStub(kitchenChannel);
        this.accountingStub = AccountingServiceGrpc.newBlockingStub(accountingChannel);

        log.info("✓ Canaux gRPC initialisés avec succès");
    }

    /**
     * 🔄 SAGA PATTERN IMPLEMENTATION
     * 
     * Flux:
     * ÉTAPE 1: OrderService -> setStatus("APPROVAL_PENDING")
     * ÉTAPE 2: KitchenService -> createTicket()
     * ÉTAPE 3: AccountingService -> authorizeCard(amount)
     * 
     * SI SUCCÈS (amount < 100):
     *   ÉTAPE 4: orderService -> setStatus("APPROVED")
     * 
     * SI ÉCHEC (amount >= 100):
     *   COMPENSATION 1: kitchenService -> rejectTicket()
     *   COMPENSATION 2: orderService -> setStatus("REJECTED")
     */
    public void executeOrderSaga(String orderId, double amount) {
        log.info("\n=====================================================");
        log.info("🚀 DÉBUT DE L'ORCHESTRATION - SAGA PATTERN");
        log.info("Commande ID : {}", orderId);
        log.info("Montant     : {} $", amount);
        log.info("=====================================================");

        try {
            // ✅ ÉTAPE 1: Créer une commande en attente
            log.info("→ [ÉTAPE 1] Order Service - Mise à jour du statut : APPROVAL_PENDING...");
            UpdateStatusResponse statusRes = orderStub.updateStatus(
                    UpdateStatusRequest.newBuilder()
                            .setOrderId(orderId)
                            .setStatus("APPROVAL_PENDING")
                            .build()
            );

            if (!statusRes.getAcknowledged()) {
                throw new RuntimeException("❌ Impossible de mettre à jour le statut de la commande.");
            }
            log.info("✓ Commande créée en attente d'approbation");

            // ✅ ÉTAPE 2: Créer un ticket en cuisine
            log.info("→ [ÉTAPE 2] Kitchen Service - Création du ticket cuisine...");
            TicketResponse ticketRes = kitchenStub.createTicket(
                    TicketRequest.newBuilder()
                            .setOrderId(orderId)
                            .build()
            );

            if (!ticketRes.getSuccess()) {
                throw new RuntimeException("❌ La cuisine a refusé la création du ticket.");
            }
            log.info("✓ Ticket créé en cuisine");

            // ✅ ÉTAPE 3: Vérifier les fonds via le service comptable
            log.info("→ [ÉTAPE 3] Accounting Service - Vérification des fonds ({} $)...", amount);
            AuthorizeResponse authRes = accountingStub.authorizeCard(
                    AuthorizeRequest.newBuilder()
                            .setOrderId(orderId)
                            .setAmount(amount)
                            .build()
            );

            // ✅ ÉTAPE 4: VALIDATION OU COMPENSATION
            if (authRes.getAuthorized()) {
                // ✅ SUCCÈS: Valider la commande
                log.info("✓ Paiement autorisé ! (montant < 100$)");
                log.info("→ [ÉTAPE 4] Mise à jour du statut final : APPROVED");

                orderStub.updateStatus(
                        UpdateStatusRequest.newBuilder()
                                .setOrderId(orderId)
                                .setStatus("APPROVED")
                                .build()
                );

                log.info("\n✅ ===== RÉSULTAT FINAL : COMMANDE APPROUVÉE =====");
                log.info("Statut : APPROVED");
                log.info("Paiement : AUTORISÉ\n");

            } else {
                // ❌ ÉCHEC: LANCER LA COMPENSATION (ROLLBACK)
                log.warn("❌ Paiement REFUSÉ (montant >= 100$) ! Lancement de la COMPENSATION...");

                // 🔄 COMPENSATION 1: Annuler le ticket cuisine
                log.warn("→ [COMPENSATION] Annulation du ticket cuisine...");
                try {
                    RejectResponse rejectRes = kitchenStub.rejectTicket(
                            RejectRequest.newBuilder()
                                    .setOrderId(orderId)
                                    .build()
                    );
                    if (rejectRes.getAcknowledged()) {
                        log.warn("  ✓ Ticket cuisine annulé avec succès");
                    }
                } catch (Exception e) {
                    log.error("  ❌ Erreur lors de l'annulation du ticket cuisine : {}", e.getMessage());
                }

                // 🔄 COMPENSATION 2: Marquer la commande comme rejetée
                log.warn("→ [COMPENSATION] Mise à jour du statut : REJECTED");
                try {
                    UpdateStatusResponse rejectStatusRes = orderStub.updateStatus(
                            UpdateStatusRequest.newBuilder()
                                    .setOrderId(orderId)
                                    .setStatus("REJECTED")
                                    .build()
                    );
                    if (rejectStatusRes.getAcknowledged()) {
                        log.warn("  ✓ Commande marquée comme rejetée");
                    }
                } catch (Exception e) {
                    log.error("  ❌ Erreur lors du rejet de la commande : {}", e.getMessage());
                }

                log.info("\n❌ ===== RÉSULTAT FINAL : COMMANDE REJETÉE =====");
                log.info("Statut : REJECTED");
                log.info("Raison : Paiement refusé (montant >= 100$)\n");
            }

        } catch (Exception e) {
            log.error("\n❌ !!! ERREUR CRITIQUE DURANT L'ORCHESTRATION !!!");
            log.error("Détails : {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de l'orchestration du Saga", e);
        }
    }

    public void shutdown() {
        try {
            if (orderChannel != null) orderChannel.shutdown();
            if (kitchenChannel != null) kitchenChannel.shutdown();
            if (accountingChannel != null) accountingChannel.shutdown();
            log.info("✓ Canaux gRPC fermés avec succès");
        } catch (Exception e) {
            log.error("Erreur lors de la fermeture des canaux : {}", e.getMessage());
        }
    }
}
```

---

## 4️⃣ PERSISTENCE LAYER - Entités et Repositories

### 🎯 Rôle de la Couche Persistence

- **Entités JPA** : Représentent les données en base PostgreSQL
- **Repositories** : Interfaces pour accéder aux données (CRUD)
- **Migrations Flyway** : Créent les tables au démarrage

### 📝 Entité: `src/main/java/org/example/order/model/Order.java`

```java
package org.example.order.model;

import jakarta.persistence.*;

@Entity              // ✅ Annotation JPA
@Table(name = "orders")  // ✅ Nom de la table en base
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // Clé primaire auto-incrémentée

    @Column(nullable = false)
    private String customerName;  // Nom du client

    @Column(nullable = false)
    private String items;  // Articles commandés

    @Column(nullable = false)
    private String status;  // APPROVAL_PENDING, APPROVED, REJECTED

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

**🔑 Cartographie BD:**
```
orders (Table)
├── id (BIGINT, PK, AUTO_INCREMENT)
├── customer_name (VARCHAR, NOT NULL)
├── items (VARCHAR, NOT NULL)
└── status (VARCHAR, NOT NULL)
```

---

### 📝 Entité: `src/main/java/org/example/kitchen/model/Dish.java`

```java
package org.example.kitchen.model;

import jakarta.persistence.*;

@Entity
@Table(name = "dishes")
public class Dish {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;  // Nom du plat

    @Column(nullable = false)
    private String recipe;  // Recette

    @Column(nullable = false)
    private int preparationTime;  // Temps de préparation en minutes

    // Getters & Setters...
}
```

---

### 📝 Entité: `src/main/java/org/example/accounting/model/Invoice.java`

```java
package org.example.accounting.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoices")
public class Invoice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;  // Référence à la commande

    @Column(nullable = false)
    private BigDecimal amount;  // Montant de la facture

    @Column(nullable = false)
    private String status;  // AUTHORIZED, REJECTED, etc.

    // Getters & Setters...
}
```

---

### 📝 Repository: `src/main/java/org/example/order/repository/OrderRepository.java`

```java
package org.example.order.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.example.order.model.Order;

@Repository  // ✅ Enregistré comme composant Spring
public interface OrderRepository extends JpaRepository<Order, Long> {
    // JpaRepository fournit automatiquement:
    // - save(Order)
    // - findById(Long)
    // - findAll()
    // - delete(Order)
    // - etc.
}
```

**🔑 Points Clés:**
- Héritage de `JpaRepository<Order, Long>` : `Order` = entité, `Long` = type de clé
- Spring Data JPA génère l'implémentation automatiquement
- Méthodes CRUD disponibles: `save()`, `findById()`, `findAll()`, `delete()`

---

## 5️⃣ CONFIGURATION - Fichiers YAML

### 🎯 Rôle de la Configuration

Chaque fichier YAML configure:
- **Datasource** : Adresse PostgreSQL, credentials
- **JPA/Hibernate** : DDL auto, dialect
- **Flyway** : Migrations SQL
- **Server Port** : Port HTTP pour REST
- **gRPC Server Port** : Port gRPC pour les services

### 📝 Fichier: `src/main/resources/application.yml` (Configuration commune)

```yaml
spring:
  application:
    name: microservices-app
  jpa:
    show-sql: true                     # ✅ Affiche les requêtes SQL
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect  # ✅ Dialect PostgreSQL
    hibernate:
      ddl-auto: validate               # ✅ Juste valider, ne pas créer
  flyway:
    enabled: true                      # ✅ Activer Flyway
    locations: classpath:db/migration  # ✅ Répertoire des migrations
  datasource:
    driver-class-name: org.postgresql.Driver
    username: postgres                 # ✅ Par défaut PostgreSQL
    password: password                 # ✅ Password par défaut

server:
  port: 8080                          # ✅ Port HTTP REST

grpc:
  server:
    port: 9090                        # ✅ Port gRPC par défaut
```

---

### 📝 Fichier: `src/main/resources/application-order.yml` (Profil Order Service)

```yaml
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5431/order_db  # ✅ Port unique pour Order
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 9091                           # ✅ Port HTTP unique

grpc:
  server:
    port: 9091                         # ✅ Port gRPC = Port HTTP
```

**🔑 Points Clés:**
- `url: jdbc:postgresql://localhost:5431/order_db` : Pointe vers DB Order
- `server.port: 9091` : Service REST écoute sur 9091
- `grpc.server.port: 9091` : Service gRPC écoute aussi sur 9091

---

### 📝 Fichier: `src/main/resources/application-kitchen.yml` (Profil Kitchen Service)

```yaml
spring:
  application:
    name: kitchen-service
  datasource:
    url: jdbc:postgresql://localhost:5432/kitchen_db  # ✅ Port unique pour Kitchen
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 9092                           # ✅ Port HTTP unique

grpc:
  server:
    port: 9092                         # ✅ Port gRPC = Port HTTP
```

---

### 📝 Fichier: `src/main/resources/application-accounting.yml` (Profil Accounting Service)

```yaml
spring:
  application:
    name: accounting-service
  datasource:
    url: jdbc:postgresql://localhost:5433/accounting_db  # ✅ Port unique pour Accounting
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 9093                           # ✅ Port HTTP unique

grpc:
  server:
    port: 9093                         # ✅ Port gRPC = Port HTTP
```

---

## 6️⃣ DOCKER COMPOSE - Orchestration des Bases de Données

### 📝 Fichier: `docker-compose.yml`

```yaml
services:
  order-db:                        # ✅ Conteneur de la DB Order
    image: postgres:16
    environment:
      POSTGRES_DB: order_db        # ✅ Nom de la base
      POSTGRES_PASSWORD: password
    ports:
      - "5431:5432"                # ✅ Port externe: 5431 → interne: 5432

  kitchen-db:                      # ✅ Conteneur de la DB Kitchen
    image: postgres:16
    environment:
      POSTGRES_DB: kitchen_db
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"                # ✅ Port externe: 5432 (standard PostgreSQL)

  accounting-db:                   # ✅ Conteneur de la DB Accounting
    image: postgres:16
    environment:
      POSTGRES_DB: accounting_db
      POSTGRES_PASSWORD: password
    ports:
      - "5433:5432"                # ✅ Port externe: 5433 → interne: 5432
```

**🔑 Points Clés:**
- Trois services PostgreSQL indépendants
- Chacun expose son propre port externe
- Flyway automatiquement applique les migrations au démarrage

---

## 7️⃣ FLUX COMPLET D'UNE REQUÊTE

### 🔄 Scénario: Une commande autorisée (montant < 100)

```
ÉTAPE 0: CLIENT REST
┌─────────────────────────────────────────────────────────────┐
│ POST /api/orchestrator/execute-saga?orderId=ORDER_001&amount=50 │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 1: OrderOrchestratorController
┌─────────────────────────────────────────────────────────────┐
│ @RestController (port 9091 HTTP)                            │
│ Reçoit: orderId = "ORDER_001", amount = 50.0                │
│ Action: Appelle orderOrchestrator.executeOrderSaga()        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 2: OrderOrchestrator.executeOrderSaga()
┌─────────────────────────────────────────────────────────────┐
│ 🔄 SAGA START                                               │
│                                                              │
│ 2.1) Appel OrderService via gRPC (port 9091)                │
│      Request: UpdateStatusRequest(orderId, "APPROVAL_PENDING")
│      Response: UpdateStatusResponse(acknowledged=true)      │
│                                                              │
│ 2.2) Appel KitchenService via gRPC (port 9092)              │
│      Request: TicketRequest(orderId)                        │
│      Response: TicketResponse(success=true)                 │
│                                                              │
│ 2.3) Appel AccountingService via gRPC (port 9093)           │
│      Request: AuthorizeRequest(orderId, amount=50.0)        │
│      Response: AuthorizeResponse(authorized=true)  ✅       │
│      (car 50.0 < 100.0)                                     │
│                                                              │
│ 2.4) Procédure SUCCÈS:                                      │
│      Appel OrderService: setStatus("APPROVED")              │
│      Response: UpdateStatusResponse(acknowledged=true)      │
│ 🔄 SAGA COMPLETE (SUCCESS)                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 3: BasesPostgreSQL
┌─────────────────────────────────────────────────────────────┐
│ order_db (port 5431):                                       │
│   UPDATE orders SET status = 'APPROVED' WHERE id = 1        │
│                                                              │
│ kitchen_db (port 5432):                                     │
│   INSERT INTO dishes (name, recipe, prep_time) VALUES (...)  │
│                                                              │
│ accounting_db (port 5433):                                  │
│   INSERT INTO invoices (orderId, amount, status) VALUES (...) │
└─────────────────────────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 4: Réponse REST au Client
┌─────────────────────────────────────────────────────────────┐
│ HTTP 200 OK                                                 │
│ {                                                           │
│   "orderId": "ORDER_001",                                   │
│   "amount": 50.0,                                           │
│   "success": true,                                          │
│   "message": "✅ Saga exécuté avec succès"                  │
│ }                                                           │
└─────────────────────────────────────────────────────────────┘
```

---

### 🔄 Scénario: Une commande rejetée (montant >= 100) - COMPENSATION

```
ÉTAPE 0: CLIENT REST
┌──────────────────────────────────────────────────────────────┐
│ POST /api/orchestrator/execute-saga?orderId=ORDER_002&amount=150 │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 1 à 3: (Identique au scénario succès jusqu'à l'AccountingService)

ÉTAPE 3.1: AccountingService Response
┌──────────────────────────────────────────────────────────────┐
│ AuthorizeResponse(authorized=false) ❌                       │
│ (car 150.0 >= 100.0)                                         │
│ → DÉCLENCHE LA COMPENSATION                                  │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 3.2: COMPENSATION 1 - Annuler le ticket cuisine
┌──────────────────────────────────────────────────────────────┐
│ Appel KitchenService.rejectTicket() via gRPC (port 9092)     │
│ Request: RejectRequest(orderId)                              │
│ Response: RejectResponse(acknowledged=true)                  │
│ ✓ Ticket annulé en cuisine                                   │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 3.3: COMPENSATION 2 - Marquer la commande comme rejetée
┌──────────────────────────────────────────────────────────────┐
│ Appel OrderService.updateStatus() via gRPC (port 9091)       │
│ Request: UpdateStatusRequest(orderId, "REJECTED")            │
│ Response: UpdateStatusResponse(acknowledged=true)            │
│ ✓ Commande marquée comme REJECTED                            │
│ 🔄 SAGA COMPLETE (COMPENSATION EXECUTED)                     │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 4: BasesPostgreSQL
┌──────────────────────────────────────────────────────────────┐
│ order_db (port 5431):                                        │
│   UPDATE orders SET status = 'REJECTED' WHERE id = 2         │
│                                                              │
│ kitchen_db (port 5432):                                      │
│   DELETE FROM dishes WHERE orderId = 'ORDER_002' (ou update) │
│                                                              │
│ accounting_db (port 5433):                                   │
│   INSERT INTO invoices (orderId, amount, status='REJECTED') │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
ÉTAPE 5: Réponse REST au Client (exception/error handling)
┌──────────────────────────────────────────────────────────────┐
│ HTTP 500 INTERNAL SERVER ERROR (ou 400)                      │
│ {                                                            │
│   "orderId": "ORDER_002",                                    │
│   "amount": 150.0,                                           │
│   "success": false,                                          │
│   "message": "❌ Erreur: ... Compensation exécutée"         │
│ }                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 8️⃣ TABLES DE MIGRATION FLYWAY

### 📝 Fichier: `src/main/resources/db/migration/V1__init.sql`

```sql
-- Table pour Order Service (order_db)
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    items TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table pour Kitchen Service (kitchen_db)
CREATE TABLE IF NOT EXISTS dishes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    recipe TEXT NOT NULL,
    preparation_time INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table pour Accounting Service (accounting_db)
CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**🔑 Exécution:**
- Flyway lit ce fichier au démarrage de chaque service
- Chaque table est créée dans sa base de données respective
- `migration` est appliquée une seule fois (versionnée)

---

## 9️⃣ EXEMPLE DE DÉMARRAGE

### 🚀 Étape 1: Démarrer les bases de données

```bash
docker-compose up -d
```

Vérification:
```bash
docker ps
```

---

### 🚀 Étape 2: Démarrer les microservices

```bash
# Terminal 1: Order Service
java -jar target/Lab5-1.0-SNAPSHOT.jar --spring.profiles.active=order

# Terminal 2: Kitchen Service
java -jar target/Lab5-1.0-SNAPSHOT.jar --spring.profiles.active=kitchen

# Terminal 3: Accounting Service
java -jar target/Lab5-1.0-SNAPSHOT.jar --spring.profiles.active=accounting
```

---

### 🚀 Étape 3: Tester via REST

```bash
# Test 1: Commande approuvée (montant < 100)
curl -X POST "http://localhost:9091/api/orchestrator/execute-saga?orderId=ORDER_001&amount=50.0"

# Test 2: Commande rejetée avec compensation (montant >= 100)
curl -X POST "http://localhost:9091/api/orchestrator/execute-saga?orderId=ORDER_002&amount=150.0"

# Test 3: Health check
curl "http://localhost:9091/api/orchestrator/health"
```

---

## 🎯 RÉSUMÉ DE L'ARCHITECTURE

| Composant | Rôle | Port | Package |
|-----------|------|------|---------|
| **Protobuf** | Définit le contrat gRPC | N/A | `com.gourmet.*` |
| **ServiceImpl** | Implémente les RPC | gRPC | `org.example.*` |
| **Orchestrator** | Saga Pattern | REST: 9091 | `org.example.orchestrator` |
| **Entity** | Données JPA | DB | `org.example.*.model` |
| **Repository** | Accès données | DB | `org.example.*.repository` |
| **Config YAML** | Configuration | N/A | `resources/` |
| **Docker** | Bases de données | 5431-5433 | N/A |
| **Flyway** | Migrations SQL | DB | `resources/db/migration/` |

---

## ✅ Points Clés à Retenir

1. **Protobuf** 📝 : Définit le "contrat" entre services
2. **@GrpcService** 🔧 : Spring Boot découvre automatiquement les services
3. **Saga Pattern** 🔄 : Orchestration de transactions distribuées
4. **Compensation** 🔙 : Rollback automatique en cas d'échec
5. **gRPC Channels** 🌉 : Connexions TCP persistantes entre services
6. **JPA + Flyway** 💾 : Persistence des données + migrations
7. **Profils Application YAML** ⚙️ : Configuration par service
8. **Docker Compose** 🐳 : Orchestration des conteneurs


