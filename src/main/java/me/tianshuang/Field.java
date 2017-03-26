package me.tianshuang;

import lombok.Data;

/**
 * Created by Poison on 8/3/2016.
 */
@Data
class Field {

    private Class clazz;
    private String name;

    Field(Class clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

}
