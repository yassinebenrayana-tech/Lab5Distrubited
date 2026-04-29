package org.example.kitchen.model;

import jakarta.persistence.*;

@Entity
@Table(name = "dishes")
public class Dish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // <-- Baddelna Long b-Integer houni

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String recipe;

    @Column(nullable = false)
    private int preparationTime;

    // Getters & Setters
    public Integer getId() { return id; } // <-- Salla7 el Return type
    public void setId(Integer id) { this.id = id; } // <-- Salla7 el Parameter type

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRecipe() { return recipe; }
    public void setRecipe(String recipe) { this.recipe = recipe; }

    public int getPreparationTime() { return preparationTime; }
    public void setPreparationTime(int preparationTime) { this.preparationTime = preparationTime; }
}