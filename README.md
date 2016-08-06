# mysql_table_toJavaBean
[![Build Status](https://travis-ci.org/tianshuang/mysql_table_toJavaBean.svg?branch=master)](https://travis-ci.org/tianshuang/mysql_table_toJavaBean)
[![Dependency Status](https://www.versioneye.com/user/projects/5797045a4fe91800287177ba/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/5797045a4fe91800287177ba)

> convert mysql table structure to java bean

### Prerequisites
- Java 7 and above

Here is a quick example:
```
java -Dfile.encoding=UTF8 -jar mysql_table_toJavaBean.jar -url jdbc:mysql://host/database -tables ******** -username ******** -password ********
```

### Supported Parameters

#### required

&#128288;``url``<br/>
This property sets the jdbc url.

&#128288;``username``<br/>
This property sets the authentication username used when obtaining connection.

&#128288;``password``<br/>
This property sets the authentication password used when obtaining connection.

#### optional

&#128288;``tables``<br/>
If you do not set this property, it will convert all tables in the database. available multiple tables separated by commas.

&#128288;``packageName``<br/>
This property sets the Java Bean belongs to.

&#10062;``useLombok``<br/>
if use this property, it will generate annotation @Data on class level instead of getter and setter methods in class.

&#10062;``useLocalDateTime``<br/>
if use this property, it will generate java.time.LocalDateTime instead of java.util.Date when you run this jar with JDK8+.

### Contributions
Welcome to contribute.