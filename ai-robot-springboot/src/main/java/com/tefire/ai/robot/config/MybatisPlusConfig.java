package com.tefire.ai.robot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;

import javax.sql.DataSource;

/**
 * MyBatis Plus 配置类
 * 手动配置 SqlSessionFactory，确保 MyBatis Plus 增强功能正常工作
 */
@Configuration
@MapperScan("com.tefire.ai.robot.domain.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        return sessionFactory;
    }
}
