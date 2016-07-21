package me.tianshuang;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.CaseFormat;
import com.mysql.jdbc.StringUtils;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.Data;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    @Parameter(names = "-packageName", required = true)
    private String packageName;

    public static void main(String[] args) {
        GenerateJavaCodeFromMysql generateJavaCodeFromMysql = new GenerateJavaCodeFromMysql();
        new JCommander(generateJavaCodeFromMysql, args);
        generateJavaCodeFromMysql.run();
    }

    private void run() {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {

            Map<String, String> fieldCommentMap = new HashMap<>();
            ResultSet fieldCommentResultSet = connection.prepareStatement("SHOW FULL COLUMNS FROM " + table).executeQuery();
            while (fieldCommentResultSet.next()) {
                fieldCommentMap.put(fieldCommentResultSet.getNString("Field"), fieldCommentResultSet.getNString("Comment"));
            }

            PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + table + " LIMIT 1");
            ResultSet resultSet = preparedStatement.executeQuery();
            ResultSetMetaData metadata = resultSet.getMetaData();

            TypeSpec.Builder builder = TypeSpec.classBuilder(lowerUnderscoreToUpperCamel(table)).addAnnotation(Data.class)
                    .addModifiers(Modifier.PUBLIC);
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String columnClass = metadata.getColumnClassName(i);
                Class clazz = null;
                switch (columnClass) {
                    case "java.math.BigInteger":
                    case "java.lang.Long":
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
                    default:
                        System.out.println(columnClass);
                }
                String fieldName = lowerUnderscoreToLowerCamel(metadata.getColumnName(i));
                FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(clazz, fieldName)
                        .addModifiers(Modifier.PRIVATE);
                String fieldComment = fieldCommentMap.get(metadata.getColumnName(i));
                if (!StringUtils.isNullOrEmpty(fieldComment)) {
                    fieldSpecBuilder.addJavadoc(fieldComment + "\n");
                }
                builder.addField(fieldSpecBuilder.build());
            }

            JavaFile javaFile = JavaFile.builder(packageName, builder.build())
                    .build();

            javaFile.writeTo(Paths.get("."));

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private String lowerUnderscoreToLowerCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, string);
    }

    private String lowerUnderscoreToUpperCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string);
    }

}
