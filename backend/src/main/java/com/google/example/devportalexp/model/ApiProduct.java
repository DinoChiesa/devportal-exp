package com.google.example.devportalexp.model;

// Simple record to represent an API Product
// Records automatically generate constructor, getters, equals(), hashCode(), toString()
public record ApiProduct(String id, String name, String description, String specUrl) {}
