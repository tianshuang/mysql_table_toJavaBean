package me.tianshuang;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.CaseFormat;
import com.mysql.cj.core.util.StringUtils;
import com.squareup.javapoet.*;
import lombok.Data;
import org.apache.commons.lang3.SystemUtils;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    @Parameter(names = "-tables")
    private List<String> tables = new ArrayList<>();
    @Parameter(names = "-packageName")
    private String packageName = "";
    @Parameter(names = "-useLocalDateTime")
    private boolean useLocalDateTime;
    @Parameter(names = "-useLombok")
    private boolean useLombok;

    public static void main(String[] args) {
        GenerateJavaCodeFromMysql generateJavaCodeFromMysql = new GenerateJavaCodeFromMysql();
        new JCommander(generateJavaCodeFromMysql, args);
        generateJavaCodeFromMysql.run();
    }

    private void run() {

        if (url.contains("?")) {
            url += "&serverTimezone=UTC";
        } else {
            url += "?serverTimezone=UTC";
        }

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            if (tables.isEmpty()) {
                DatabaseMetaData databaseMetaData = connection.getMetaData();
                ResultSet resultSet = databaseMetaData.getTables(null, null, "%", null);
                while (resultSet.next()) {
                    tables.add(resultSet.getString(3));
                }
            }

            for (String table : tables) {
                Map<String, String> fieldCommentMap = new HashMap<>();
                try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW FULL COLUMNS FROM " + table)) {
                    ResultSet fieldCommentResultSet = preparedStatement.executeQuery();
                    while (fieldCommentResultSet.next()) {
                        fieldCommentMap.put(fieldCommentResultSet.getNString("Field"), fieldCommentResultSet.getNString("Comment"));
                    }
                }

                try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + table + " LIMIT 1")) {
                    ResultSet resultSet = preparedStatement.executeQuery();
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    TypeSpec.Builder builder = TypeSpec.classBuilder(lowerUnderscoreToUpperCamel(table)).addModifiers(Modifier.PUBLIC);

                    List<Field> fieldList = new ArrayList<>();

                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String columnClass = metadata.getColumnClassName(i);
                        Class clazz = null;
                        switch (columnClass) {
                            case "java.lang.Integer":
                                clazz = Integer.class;
                                break;
                            case "java.lang.String":
                                clazz = String.class;
                                break;
                            case "java.lang.Long":
                                clazz = Long.class;
                                break;
                            case "java.lang.Boolean":
                                clazz = Boolean.class;
                                break;
                            case "java.lang.Float":
                                clazz = Float.class;
                                break;
                            case "java.lang.Double":
                                clazz = Double.class;
                                break;
                            case "java.math.BigDecimal":
                                clazz = BigDecimal.class;
                                break;
                            case "java.math.BigInteger":
                                clazz = BigInteger.class;
                                break;
                            case "java.sql.Date":
                            case "java.sql.Time":
                            case "java.sql.Timestamp":
                                if (useLocalDateTime && (SystemUtils.IS_JAVA_1_8 || SystemUtils.IS_JAVA_1_9)) {
                                    clazz = LocalDateTime.class;
                                } else {
                                    clazz = java.util.Date.class;
                                }
                                break;
                            case "[B":
                                clazz = byte[].class;
                                break;
                            default:
                                System.out.println(columnClass);
                        }
                        String fieldName = lowerUnderscoreToLowerCamel(metadata.getColumnName(i));
                        FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(clazz, fieldName)
                                .addModifiers(Modifier.PRIVATE);
                        fieldList.add(new Field(clazz, fieldName));
                        String fieldComment = fieldCommentMap.get(metadata.getColumnName(i));
                        if (!StringUtils.isNullOrEmpty(fieldComment)) {
                            fieldSpecBuilder.addJavadoc(fieldComment + "\n");
                        }
                        builder.addField(fieldSpecBuilder.build());
                    }

                    if (useLombok) {
                        builder.addAnnotation(Data.class);
                    } else {
                        generateGetterAndSetter(builder, fieldList);
                    }

                    JavaFile javaFile = JavaFile.builder(packageName, builder.build()).skipJavaLangImports(true).indent("    ").build();
                    javaFile.writeTo(Paths.get("."));
                }
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private void generateGetterAndSetter(TypeSpec.Builder builder, List<Field> fieldList) {
        for (Field field : fieldList) {
            MethodSpec.Builder getMethodSpecBuilder;
            if (field.getClazz() == Boolean.class) {
                getMethodSpecBuilder = MethodSpec.methodBuilder("is" + lowerCamelToUpperCamel(field.getName()));
            } else {
                getMethodSpecBuilder = MethodSpec.methodBuilder("get" + lowerCamelToUpperCamel(field.getName()));
            }
            MethodSpec getMethodSpec = getMethodSpecBuilder.addModifiers(Modifier.PUBLIC).returns(field.getClazz()).addStatement("return " + field.getName()).build();
            builder.addMethod(getMethodSpec);

            MethodSpec setMethodSpec = MethodSpec.methodBuilder("set" + lowerCamelToUpperCamel(field.getName())).addModifiers(Modifier.PUBLIC).returns(TypeName.VOID).addParameter(field.getClazz(), field.getName()).addStatement("this." + field.getName() + " = " + field.getName()).build();
            builder.addMethod(setMethodSpec);
        }
    }

    private String lowerUnderscoreToLowerCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, string);
    }

    private String lowerUnderscoreToUpperCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string);
    }

    private String lowerCamelToUpperCamel(String string) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, string);
    }

}
