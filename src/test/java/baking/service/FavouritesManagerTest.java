package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FavouritesManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void addsWithoutDuplicatesAndPersistsAcrossManagerInstances() {
        Path file = temporaryDirectory.resolve("data/favourites.json");
        Recipe cookie = recipe(1, "Cookie");
        FavouritesManager manager = new FavouritesManager(file);
        assertTrue(manager.addFavourite(cookie));
        assertFalse(manager.addFavourite(recipe(1, "Duplicate ID")));
        assertEquals(1, manager.getFavourites().size());

        FavouritesManager reloaded = new FavouritesManager(file);
        assertEquals("Cookie", reloaded.getFavourites().getFirst().getName());
        assertEquals(2, reloaded.getFavourites().getFirst().getIngredients().getFirst().getQuantity());
        assertFalse(reloaded.addFavourite(cookie));
    }

    @Test
    void removesAndPersistsRemoval() {
        Path file = temporaryDirectory.resolve("favourites.json");
        Recipe cookie = recipe(2, "Cookie");
        FavouritesManager manager = new FavouritesManager(file);
        assertTrue(manager.addFavourite(cookie));
        assertTrue(manager.removeFavourite(recipe(2, "Different object")));
        assertFalse(manager.removeFavourite(cookie));
        assertTrue(new FavouritesManager(file).getFavourites().isEmpty());
    }

    @Test
    void returnedFavouritesAreReadOnly() {
        FavouritesManager manager = new FavouritesManager(temporaryDirectory.resolve("favourites.json"));
        manager.addFavourite(recipe(3, "Loaf"));
        assertThrows(UnsupportedOperationException.class,
                () -> manager.getFavourites().clear());
    }

    private static Recipe recipe(int id, String name) {
        return new Recipe(id, name, "Test bake", 4, 12, 20,
                List.of(new Ingredient("flour", 2, "cups")), List.of("Mix", "Bake"), List.of("vegetarian"));
    }
}
