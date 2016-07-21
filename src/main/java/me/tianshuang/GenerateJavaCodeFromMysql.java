package me.tianshuang;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.Data;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Created by Poison on 7/21/2016.
 */
public class GenerateJavaCodeFromMysql {

    @Parameter(names = "-url", required = true)
    private String url;
    @Parameter(names = "-username", required = true)
    private String username;
    @Parameter(names = "-password", required = true)
    private String password;
    @Parameter(names = "-table", required = true)
    private String table;

    public static void main(String[] args) {
        GenerateJavaCodeFromMysql metadataColumn = new GenerateJavaCodeFromMysql();
        new JCommander(metadataColumn, args);
        metadataColumn.run();
    }

    private void run() {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {

            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM " + table + " LIMIT 1");
            ResultSetMetaData metadata = resultSet.getMetaData();

            TypeSpec.Builder helloWorldBuilder = TypeSpec.classBuilder("HelloWorld").addAnnotation(Data.class)
                    .addModifiers(Modifier.PUBLIC);
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String columnClass = metadata.getColumnClassName(i);
                Class clazz = null;
                switch (columnClass) {
                    case "java.math.BigInteger":
                        clazz = Long.class;
                        break;
                    case "java.lang.Integer":
                        clazz = Integer.class;
                        break;
                    case "java.lang.String":
                        clazz = String.class;
                        break;
                    case "java.sql.Timestamp":
                        clazz = LocalDateTime.class;
                        break;
                }
                String columnName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, metadata.getColumnName(i));
                helloWorldBuilder.addField(clazz, columnName, Modifier.PRIVATE);
            }

            TypeSpec helloWorld = helloWorldBuilder.build();

            JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
                    .build();

            javaFile.writeTo(Paths.get("."));

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}
