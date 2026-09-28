package org.tibiawalk.core.metadata;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class MetadataJson {

    private MetadataJson() {
    }

    static Gson gson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .registerTypeAdapter(LooktypeKind.class, new com.google.gson.TypeAdapter<LooktypeKind>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, LooktypeKind value) throws java.io.IOException {
                        out.value(value.name().toLowerCase());
                    }

                    @Override
                    public LooktypeKind read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        return LooktypeKind.valueOf(in.nextString().toUpperCase());
                    }
                })
                .registerTypeAdapter(GameCharacter.Kind.class, new com.google.gson.TypeAdapter<GameCharacter.Kind>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, GameCharacter.Kind value) throws java.io.IOException {
                        out.value(value.name().toLowerCase());
                    }

                    @Override
                    public GameCharacter.Kind read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        return GameCharacter.Kind.valueOf(in.nextString().toUpperCase());
                    }
                })
                .disableHtmlEscaping()
                .create();
    }
}
