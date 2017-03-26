package me.tianshuang;

import com.alibaba.fastjson.JSON;
import com.google.common.base.CaseFormat;
import com.mysql.cj.core.util.StringUtils;
import com.squareup.javapoet.*;
import lombok.Data;
import org.apache.commons.lang3.SystemUtils;

import javax.lang.model.element.Modifier;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.tianshuang.constant.Constant.*;
import static me.tianshuang.constant.SqlConstant.*;

/**
 * Created by Poison on 7/21/2016.
 */
public class GenerateJavaCodeFromMysql {

    public static void main(String[] args) throws IOException {

        try (FileInputStream fileInputStream = new FileInputStream(CONFIG_FILE)) {

            Config config = JSON.parseObject(fileInputStream, Config.class);

            optimizeUrl(config);

            try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword())) {

                fillTableListIfEmpty(connection, config.getTableList());

                for (String table : config.getTableList()) {
                    Map<String, String> fieldCommentMap = new HashMap<>();
                    fillFieldAndComment(connection, table, fieldCommentMap);

                    try (PreparedStatement preparedStatement = connection.prepareStatement(String.format(SELECT_ALL_FROM_TABLE_LIMIT_ONE, table))) {
                        ResultSet resultSet = preparedStatement.executeQuery();
                        ResultSetMetaData metadata = resultSet.getMetaData();
                        TypeSpec.Builder builder = TypeSpec.classBuilder(lowerUnderscoreToUpperCamel(table)).addModifiers(Modifier.PUBLIC);

                        List<Field> fieldList = new ArrayList<>();

                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            String columnClass = metadata.getColumnClassName(i);
                            Class clazz;
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
                                    if (config.isUseLocalDate() && (SystemUtils.IS_JAVA_1_8 || SystemUtils.IS_JAVA_1_9)) {
                                        clazz = LocalDate.class;
                                    } else {
                                        clazz = java.util.Date.class;
                                    }
                                    break;
                                case "java.sql.Time":
                                case "java.sql.Timestamp":
                                    if (config.isUseLocalDateTime() && (SystemUtils.IS_JAVA_1_8 || SystemUtils.IS_JAVA_1_9)) {
                                        clazz = LocalDateTime.class;
                                    } else {
                                        clazz = java.util.Date.class;
                                    }
                                    break;
                                default:
                                    clazz = byte[].class;
                                    break;
                            }
                            String fieldName = lowerUnderscoreToLowerCamel(metadata.getColumnName(i));
                            FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(clazz, fieldName)
                                    .addModifiers(Modifier.PRIVATE);
                            fieldList.add(new Field(clazz, fieldName));
                            String fieldComment = fieldCommentMap.get(metadata.getColumnName(i));
                            if (!StringUtils.isNullOrEmpty(fieldComment)) {
                                fieldSpecBuilder.addJavadoc(fieldComment + System.getProperty(LINE_SEPARATOR));
                            }
                            builder.addField(fieldSpecBuilder.build());
                        }

                        if (config.isUseLombok()) {
                            builder.addAnnotation(Data.class);
                        } else {
                            generateGetterAndSetter(builder, fieldList);
                        }

                        JavaFile javaFile = JavaFile.builder(config.getPackageName(), builder.build()).skipJavaLangImports(true).indent(FOUR_SPACE).build();
                        javaFile.writeTo(Paths.get("."));
                    }
                }
            } catch (SQLException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void fillFieldAndComment(Connection connection, String table, Map<String, String> fieldCommentMap) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(String.format(SHOW_FULL_COLUMNS_FROM_TABLE, table))) {
            ResultSet fieldCommentResultSet = preparedStatement.executeQuery();
            while (fieldCommentResultSet.next()) {
                fieldCommentMap.put(fieldCommentResultSet.getNString(FIELD), fieldCommentResultSet.getNString(COMMENT));
            }
        }
    }

    private static void optimizeUrl(Config config) {
        if (config.getUrl().contains("?")) {
            config.setUrl(config.getUrl() + "&serverTimezone=UTC");
        } else {
            config.setUrl(config.getUrl() + "?serverTimezone=UTC");
        }
    }

    private static void fillTableListIfEmpty(Connection connection, List<String> tableList) throws SQLException {
        if (tableList.isEmpty()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet resultSet = databaseMetaData.getTables(null, null, "%", null);
            while (resultSet.next()) {
                tableList.add(resultSet.getString(3));
            }
        }
    }

    private static void generateGetterAndSetter(TypeSpec.Builder builder, List<Field> fieldList) {
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

    private static String lowerUnderscoreToLowerCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, string);
    }

    private static String lowerUnderscoreToUpperCamel(String string) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string);
    }

    private static String lowerCamelToUpperCamel(String string) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, string);
    }

}
