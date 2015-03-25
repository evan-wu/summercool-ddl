# summercool-ddl
Automatically exported from code.google.com/p/summercool-ddl


1.依赖
Xml代码  收藏代码
<dependency>  
    <groupId>org.summercool</groupId>  
    <artifactId>summercool-ddl</artifactId>  
    <version>1.0</version>  
</dependency>  
 源码svn地址：http://summercool-ddl.googlecode.com/svn/trunk
2.准备Sql映射文件
Xml代码  收藏代码
<?xml version="1.0" encoding="UTF-8" ?>   
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" ">  
<mapper namespace="NS-User">  
    <insert id="insertUser" parameterType="org.summercool.mybatis.demo.dao.entity.UserEntity">  
        insert into $[user]$(  
            id,  
            name  
        ) values (  
            #{id},  
            #{name}  
        )  
    </insert>  
</mapper>  
 注意此处，对于需要分表或分库的表，使用“$[table name]$”这种方式表示。
3.Spring配置
Xml代码  收藏代码
<?xml version="1.0" encoding="UTF-8"?>  
<beans xmlns="http://www.springframework.org/schema/beans"  
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:util="http://www.springframework.org/schema/util"  
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd  
        http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-3.0.xsd"  
    default-autowire="byName">  
    <bean id="dataSourceMaster" class="com.jolbox.bonecp.BoneCPDataSource"  
        destroy-method="close">  
        <property name="driverClass" value="com.mysql.jdbc.Driver" />  
        <property name="jdbcUrl">  
            <value>${demo.datasource.master.jdbcUrl}</value>  
        </property>  
        <property name="username" value="${demo.datasource.master.username}" />  
        <property name="password" value="${demo.datasource.master.password}" />  
        <property name="idleConnectionTestPeriodInMinutes" value="5" />  
        <property name="idleMaxAgeInMinutes" value="30" />  
        <property name="maxConnectionsPerPartition" value="30" />  
        <property name="minConnectionsPerPartition" value="5" />  
        <property name="partitionCount" value="2" />  
        <property name="acquireIncrement" value="5" />  
        <property name="statementsCacheSize" value="150" />  
        <property name="releaseHelperThreads" value="8" />  
        <property name="connectionTestStatement" value="select 1 from dual" />  
    </bean>  
       
    <bean id="dataSourceSlave" class="com.jolbox.bonecp.BoneCPDataSource"  
        destroy-method="close">  
        <property name="driverClass" value="com.mysql.jdbc.Driver" />  
        <property name="jdbcUrl">  
            <value>${demo.datasource.slave.jdbcUrl}</value>  
        </property>  
        <property name="username" value="${demo.datasource.slave.username}" />  
        <property name="password" value="${demo.datasource.slave.password}" />  
        <property name="idleConnectionTestPeriodInMinutes" value="5" />  
        <property name="idleMaxAgeInMinutes" value="30" />  
        <property name="maxConnectionsPerPartition" value="30" />  
        <property name="minConnectionsPerPartition" value="5" />  
        <property name="partitionCount" value="2" />  
        <property name="acquireIncrement" value="5" />  
        <property name="statementsCacheSize" value="150" />  
        <property name="releaseHelperThreads" value="8" />  
        <property name="connectionTestStatement" value="select 1 from dual" />  
    </bean>  
    <bean id="demoSqlSessionFactory"  
        class="org.summercool.mybatis.spring.support.SqlSessionFactoryBean">  
        <property name="mainDataSource" ref="dataSourceMaster" />  
        <property name="shardDataSourceList">  
            <util:list>  
                <ref bean="dataSourceSlave" />  
            </util:list>  
        </property>  
        <property name="mapperLocations">  
            <array>  
                <value>classpath:mybatis/user-mapper.xml</value>  
            </array>  
        </property>  
        <property name="shardStrategy">  
            <map>  
                <entry key="Shard-User">  
                    <value>org.summercool.mybatis.demo.shard.UserShardStrategy</value>  
                </entry>  
            </map>  
        </property>  
    </bean>  
    <bean id="userDao" class="org.summercool.mybatis.demo.dao.impl.UserDaoImpl">  
        <property name="sqlSessionFactory" ref="demoSqlSessionFactory"/>  
    </bean>  
</beans>  
从上面的配置文件中，可以看到，注入了两个DataSource，分别代表两个物理库。
然后再添加类型为org.summercool.mybatis.spring.support.SqlSessionFactoryBean的bean描述，并将两个DataSource关联到该bean的主库和备库（备库可以有多个）字段。

同时，需要设置分表策略字段shardStrategy，该字段的类型是Map，添加分表策略时，Key即为分表策略的名称，Value则为具体的分表策略实现。如上，注册了一个名称为"fr"的分表策略实现类com.gexin.contact.utils.shard.ContactShardStrategy。

4.分表策略的实现
Java代码  收藏代码
package org.summercool.mybatis.demo.shard;  
import java.util.Map;  
import javax.sql.DataSource;  
import org.summercool.mybatis.ShardParam;  
import org.summercool.mybatis.strategy.ShardStrategy;  
/** 
 *  
 * @author Kolor 
 */  
public class UserShardStrategy extends ShardStrategy {  
    @Override  
    public DataSource getTargetDataSource() {  
        ShardParam shardParam = getShardParam();  
        //  
        Long param = (Long) shardParam.getShardValue();  
        Map<String, DataSource> map = this.getShardDataSources();  
        if (param > 100) {  
            return map.get("dataSourceSlave");  
        }  
        return getMainDataSource();  
    }  
    @Override  
    public String getTargetSql() {  
        String targetSql = getSql();  
        ShardParam shardParam = getShardParam();  
        //  
        Long param = (Long) shardParam.getShardValue();  
        String tableName = "user_" + (param % 2);  
        targetSql = targetSql.replaceAll("\\$\\[user\\]\\$", tableName);  
        return targetSql;  
    }  
}  
可以看到这个分表策略继承自org.summercool.mybatis.strategy.ShardStrategy，并实现其两个抽象方法getTargetDataSource和getTargetSql，分别代表获取目标DataSource和获取目标Sql。
目标DataSource通过解析分表参数，按设计好的策略获取。

目标Sql同样通过解析分表参数，生成真正的表名，然后用字符串替换的方式获取真正的Sql。

5.Dao层实现
Java代码  收藏代码
package org.summercool.mybatis.demo.dao.impl;  
import org.summercool.mybatis.ShardParam;  
import org.summercool.mybatis.demo.dao.UserDao;  
import org.summercool.mybatis.demo.dao.entity.UserEntity;  
import org.summercool.mybatis.spring.support.SqlSessionDaoSupport;  
/** 
 *   
 * @author 
 */  
public class UserDaoImpl extends SqlSessionDaoSupport implements UserDao {  
    public boolean insertUser(UserEntity user) {  
        ShardParam shardParam = new ShardParam("Shard-User", user.getId(), user);  
           
        return getSqlSession().insert("NS-User.insertUser", shardParam) > 0;  
    }  
}  
Dao层实现没有特别的地方，仅有的一点就是，对于需要分表分库的操作，需要传入分表参数（ShardParam）

ShardParam shardParam = new ShardParam("Shard-User", user.getId(), user);

我们来看看ShardParam的定义：

Java代码  收藏代码
package org.summercool.mybatis;  
public class ShardParam {  
    public static final ShardParam NO_SHARD = new ShardParam();  
    private String name;  
    private Object shardValue;  
    private Object params;  
    public ShardParam() {  
    }  
    public ShardParam(String name, Object shardValue, Object params) {  
        super();  
        this.name = name;  
        this.shardValue = shardValue;  
        this.params = params;  
    }  
    public String getName() {  
        return name;  
    }  
    public void setName(String name) {  
        this.name = name;  
    }  
    public Object getShardValue() {  
        return shardValue;  
    }  
    public void setShardValue(Object shardValue) {  
        this.shardValue = shardValue;  
    }  
    public Object getParams() {  
        return params;  
    }  
    public void setParams(Object params) {  
        this.params = params;  
    }  
}  
 构造函数，第一个参数是分表分库策略的名字（即在第二小节中提到的"fr"），第二个参数是分表参数，第三个参数是真正提交到数据库的参数。

6. Spring事务配置
Xml代码  收藏代码
<?xml version="1.0" encoding="UTF-8"?>  
<beans xmlns="http://www.springframework.org/schema/beans"  
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"  
    xmlns:util="http://www.springframework.org/schema/util"  
    xmlns:context="http://www.springframework.org/schema/context"  
    xmlns:tx="http://www.springframework.org/schema/tx"  
    xmlns:aop="http://www.springframework.org/schema/aop"  
    xsi:schemaLocation="http://www.springframework.org/schema/beans  
        http://www.springframework.org/schema/beans/spring-beans-3.0.xsd  
        http://www.springframework.org/schema/context  
        http://www.springframework.org/schema/context/spring-context-3.0.xsd  
        http://www.springframework.org/schema/tx  
        http://www.springframework.org/schema/tx/spring-tx-3.0.xsd  
        http://www.springframework.org/schema/aop  
        http://www.springframework.org/schema/aop/spring-aop-3.0.xsd   
        http://www.springframework.org/schema/util   
        http://www.springframework.org/schema/util/spring-util-3.0.xsd"  
    default-autowire="byName">  
       
    <bean id="propertyConfigurer" class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">  
        <property name="locations">  
            <list>  
                <value>classpath:config/config.properties</value>  
            </list>  
        </property>  
        <property name="fileEncoding" value="UTF-8" />  
    </bean>  
       
    <bean id="userService" class="org.summercool.mybatis.demo.service.impl.UserServiceImpl"/>  
       
    <!-- 事务配置 -->  
    <bean name="userTransactionManager"  
        class="org.summercool.mybatis.spring.support.MultiDataSourceTransactionManager">  
    </bean>  
    <tx:advice id="userTxAdvice" transaction-manager="userTransactionManager">  
        <tx:attributes>  
            <tx:method name="*" propagation="NOT_SUPPORTED" />  
        </tx:attributes>  
    </tx:advice>  
       
    <aop:config>  
        <!-- UserService事务管理 -->  
        <aop:pointcut id="userServiceOperation"  
            expression="execution(* org.summercool.mybatis.demo.service..*Service.*(..))" />  
        <aop:advisor advice-ref="userTxAdvice" pointcut-ref="userServiceOperation" />  
    </aop:config>  
</beans>  
 目前只支持XA分布式事务。
