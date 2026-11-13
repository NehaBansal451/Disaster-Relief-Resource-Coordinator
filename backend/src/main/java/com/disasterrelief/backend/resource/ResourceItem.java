package com.disasterrelief.backend.resource;

public record ResourceItem(long id, String name, String category, String unit, boolean perishable) {
}
