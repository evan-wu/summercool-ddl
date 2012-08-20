package org.summercool.mybatis.spring.support;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.util.ObjectUtils;
import org.summercool.mybatis.converter.DefaultSqlConverter;
import org.summercool.mybatis.converter.SqlConverter;
import org.summercool.mybatis.plugin.ShardPlugin;
import org.summercool.mybatis.strategy.ShardStrategy;

public class SqlSessionFactoryBean implements ApplicationContextAware, MultiDataSourceSupport {

	private final Log logger = LogFactory.getLog(getClass());

	private ApplicationContext applicationContext;

	private DataSource mainDataSource;

	private SqlSessionFactory mainSqlSessionFactory;

	private Map<String, DataSource> shardDataSources;

	private Map<String, SqlSessionFactory> shardSqlSessionFactory;

	private List<DataSource> shardDataSourceList;

	private Resource[] mapperLocations;

	private Map<String, ShardStrategy> shardStrategyMap = new HashMap<String, ShardStrategy>();
	private Map<String, Class<?>> shardStrategyConfig = new HashMap<String, Class<?>>();

	private SqlConverter sqlConverter = new DefaultSqlConverter();

	public DataSource getMainDataSource() {
		return mainDataSource;
	}

	public void setMainDataSource(DataSource mainDataSource) {
		if (mainDataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform
			// transactions for its underlying target DataSource, else data
			// access code won't see properly exposed transactions (i.e.
			// transactions for the target DataSource).
			this.mainDataSource = ((TransactionAwareDataSourceProxy) mainDataSource).getTargetDataSource();
		} else {
			this.mainDataSource = mainDataSource;
		}
	}

	public void setShardDataSourceList(List<DataSource> shardDataSourceList) {
		this.shardDataSourceList = shardDataSourceList;
	}

	public Map<String, DataSource> getShardDataSources() {
		return shardDataSources;
	}

	public void setMapperLocations(Resource[] mapperLocations) {
		this.mapperLocations = mapperLocations;
	}

	public void setShardStrategy(Map<String, Class<?>> shardStrategyMap) {
		this.shardStrategyConfig = shardStrategyMap;
	}

	public SqlSessionFactory getMainSqlSessionFactory() {
		return mainSqlSessionFactory;
	}

	public Map<String, SqlSessionFactory> getShardSqlSessionFactory() {
		return shardSqlSessionFactory;
	}

	public Map<String, ShardStrategy> getShardStrategyMap() {
		return shardStrategyMap;
	}

	public void afterPropertiesSet() throws Exception {
		if (mainDataSource == null && (shardDataSourceList == null || shardDataSourceList.size() == 0)) {
			throw new RuntimeException(
					" Property 'mainDataSource' and property 'shardDataSourceList' can not be null together! ");
		}
		if (shardDataSourceList != null && shardDataSourceList.size() > 0) {
			shardDataSources = new LinkedHashMap<String, DataSource>();
			Map<String, DataSource> dataSourceMap = applicationContext.getBeansOfType(DataSource.class);
			for (Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
				for (int i = 0; i < shardDataSourceList.size(); i++) {
					DataSource ds = shardDataSourceList.get(i);
					if (entry.getValue() == ds) {
						DataSource dataSource = entry.getValue();
						if (dataSource instanceof TransactionAwareDataSourceProxy) {
							dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
						}
						shardDataSources.put(entry.getKey(), dataSource);
					}
				}
			}
		}
		if (mainDataSource == null) {
			if (shardDataSourceList.get(0) instanceof TransactionAwareDataSourceProxy) {
				this.mainDataSource = ((TransactionAwareDataSourceProxy) shardDataSourceList.get(0))
						.getTargetDataSource();
			} else {
				mainDataSource = shardDataSources.get(0);
			}
		}

		this.mainSqlSessionFactory = buildSqlSessionFactory(getMainDataSource());
		if (getShardDataSources() != null && getShardDataSources().size() > 0) {
			shardSqlSessionFactory = new LinkedHashMap<String, SqlSessionFactory>(getShardDataSources().size());
			for (Entry<String, DataSource> entry : getShardDataSources().entrySet()) {
				shardSqlSessionFactory.put(entry.getKey(), buildSqlSessionFactory(entry.getValue()));
			}
		}
		//
		if (shardStrategyConfig != null) {
			shardStrategyMap = new HashMap<String, ShardStrategy>();
			for (Map.Entry<String, Class<?>> entry : shardStrategyConfig.entrySet()) {
				Class<?> clazz = entry.getValue();
				if (!ShardStrategy.class.isAssignableFrom(clazz)) {
					throw new IllegalArgumentException("class " + clazz.getName()
							+ " is illegal, subclass of ShardStrategy is required.");
				}
				try {
					shardStrategyMap.put(entry.getKey(), (ShardStrategy) (entry.getValue().newInstance()));
				} catch (Exception e) {
					throw new RuntimeException("new instance for class " + clazz.getName() + " failed, error:"
							+ e.getMessage());
				}
			}
			//
			shardStrategyConfig = null;
		}
	}

	private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) throws IOException {
		ShardPlugin plugin = new ShardPlugin();
		plugin.setSqlConverter(sqlConverter);

		Configuration configuration = null;
		SpringManagedTransactionFactory transactionFactory = null;
		//
		configuration = new Configuration();
		configuration.addInterceptor(plugin);
		//
		transactionFactory = new SpringManagedTransactionFactory(dataSource);

		Environment environment = new Environment(SqlSessionFactoryBean.class.getSimpleName(), transactionFactory,
				dataSource);
		configuration.setEnvironment(environment);

		if (!ObjectUtils.isEmpty(this.mapperLocations)) {
			for (Resource mapperLocation : this.mapperLocations) {
				if (mapperLocation == null) {
					continue;
				}
				// this block is a workaround for issue
				// http://code.google.com/p/mybatis/issues/detail?id=235
				// when running MyBatis 3.0.4. But not always works.
				// Not needed in 3.0.5 and above.
				String path;
				if (mapperLocation instanceof ClassPathResource) {
					path = ((ClassPathResource) mapperLocation).getPath();
				} else {
					path = mapperLocation.toString();
				}

				try {
					XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocation.getInputStream(),
							configuration, path, configuration.getSqlFragments());
					xmlMapperBuilder.parse();
				} catch (Exception e) {
					throw new NestedIOException("Failed to parse mapping resource: '" + mapperLocation + "'", e);
				} finally {
					ErrorContext.instance().reset();
				}

				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Parsed mapper file: '" + mapperLocation + "'");
				}
			}
		} else {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Property 'mapperLocations' was not specified or no matching resources found");
			}
		}

		return new SqlSessionFactoryBuilder().build(configuration);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
