package com.learnkafka.model;

import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity // Marca esta clase como una entidad JPA, mapeada a una tabla.
public class LibraryEvent {

    @Id // Especifica que este campo es la clave primaria
    @GeneratedValue // Configura la estrategia de generacion de claves primarias (automatica por defecto)
    private Integer libraryEventId;

    @Enumerated(EnumType.STRING) // Persiste el enum como un STRING ("NEW, "UPDATE") en lugar de un numero.
    private LibraryEventType libraryEventType;

    // Define una relacion uno a uno con la entidad book
    // mappedBy indica que la entidad Book es la dueña de la relacion y contiene la clave foranea.
    // cascade = {CascadeType.ALL} propaga todas las operaciones (guardar, eliminar, actualizar) desde LibraryEvent a su Book asociado.
    @OneToOne(mappedBy = "libraryEvent", cascade = {CascadeType.ALL})
    @ToString.Exclude // Excluye este campo del metodo toString() para evitar recurionn infinita
    private Book book;



}