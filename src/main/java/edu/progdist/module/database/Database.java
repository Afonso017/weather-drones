package edu.progdist.module.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classe que simula um banco de dados simples para armazenar os dados climáticos em memória.
 */
public class Database {
    private final List<String> storage = Collections.synchronizedList(new ArrayList<>());

    public void saveData(String data) {
        storage.add(data);
    }

    public List<String> getAllData() {
        return new ArrayList<>(storage);
    }

    public long getTotalCount() {
        return storage.size();
    }
}
