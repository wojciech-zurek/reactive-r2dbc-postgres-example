package eu.wojciechzurek.reactivepostgresexample

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.function.DatabaseClient
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory
import org.springframework.data.relational.core.mapping.RelationalMappingContext
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import javax.annotation.PostConstruct
import javax.validation.constraints.NotBlank

fun main(args: Array<String>) {
    runApplication<ReactivePostgresExampleApplication>(*args)
}

@SpringBootApplication
class ReactivePostgresExampleApplication {

    @Bean
    fun routes(employeeHandler: EmployeeHandler) = router {
        GET("/employees", employeeHandler::findAll)
        GET("/employees/{id}", employeeHandler::findById)
    }
}

@Configuration
class DBConfig {

    @Bean
    fun connectionFactory() = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration
                    .builder()
                    .host("localhost")
                    .database("postgres")
                    .username("postgres")
                    .password("mysecretpassword")
                    .build()
    )

    @Bean
    fun databaseClient(connectionFactory: ConnectionFactory): DatabaseClient =
            DatabaseClient.builder().connectionFactory(connectionFactory).build()

    @Bean
    fun repositoryFactory(databaseClient: DatabaseClient): R2dbcRepositoryFactory {

        val context = RelationalMappingContext()
        context.afterPropertiesSet()
        return R2dbcRepositoryFactory(databaseClient, context)
    }

    @Bean
    fun coffeeRepository(factory: R2dbcRepositoryFactory): EmployeeRepository {
        return factory.getRepository(EmployeeRepository::class.java)
    }
}

@Component
class InitRunner(private val client: DatabaseClient, private val employeeRepository: EmployeeRepository) {

    @PostConstruct
    fun afterPropertiesSet() {

        client
                .execute()
                .sql("CREATE TABLE IF NOT EXISTS employee ( id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL);")
                .fetch()
                .all()
                .subscribe { println(it) }

        employeeRepository
                .deleteAll()
                .thenMany(Flux.just("wojtek", "admin", "test"))
                .map { Employee(name = it) }
                .flatMap { employeeRepository.save(it) }
                .thenMany(employeeRepository.findAll())
                .subscribe { println(it) }
    }
}

@Component
class EmployeeHandler(private val employeeRepository: EmployeeRepository) {
    fun findAll(request: ServerRequest) = ok().body(employeeRepository.findAll())

    fun findById(request: ServerRequest) = employeeRepository
            .findById(request.pathVariable("id").toLong())
            .flatMap { ok().syncBody(it) }
            .switchIfEmpty(notFound().build())
}

interface EmployeeRepository : ReactiveCrudRepository<Employee, Long>

data class Employee(@Id val id: Long? = null, @NotBlank val name: String)