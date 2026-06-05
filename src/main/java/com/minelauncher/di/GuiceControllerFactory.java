package com.minelauncher.di;

import com.google.inject.Injector;
import javafx.util.Callback;

public class GuiceControllerFactory implements Callback<Class<?>, Object> {
    private final Injector injector;

    public GuiceControllerFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public Object call(Class<?> type) {
        return injector.getInstance(type);
    }
}
