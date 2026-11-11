package com.billyke2000s.moviehubtv.catalogue;

import java.nio.file.*;

public final class EngineBridgeRegression {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("build", "engine-bridge-regression").toAbsolutePath();
        var inventory = MovieHubEngine.inventory(directory).getAsJsonArray("providers");
        if (inventory.size() != 74) throw new AssertionError("Real Java-to-Python inventory must contain the Android app's 74 active services");
        boolean rive = false;
        for (var entry : inventory) if (entry.getAsJsonObject().get("id").getAsString().equals("native:rive")) rive = true;
        if (!rive || !Files.exists(directory.resolve("packages/requests/__init__.py")))
            throw new AssertionError("Bundled provider runtime and portable dependencies must be extracted by the real host code");
        System.out.println("PASS: production Java host extracts bundled engine/dependencies, starts Python and reads all 74 services");
    }
}
