package eu.wojciechzurek.reactiver2dbcpostgresexample

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
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import java.net.URI
import javax.annotation.PostConstruct
import javax.validation.constraints.NotBlank

fun main(args: Array<String>) {
    runApplication<ReactiveR2DBCPostgresExampleApplication>(*args)
}

@SpringBootApplication
class ReactiveR2DBCPostgresExampleApplication {

    @Bean
    fun routes(employeeHandler: EmployeeHandler) = router {
        GET("/employees", employeeHandler::findAll)
        GET("/employees/{id}", employeeHandler::findById)
        POST("/employees", employeeHandler::new)
        PUT("/employees/{id}", employeeHandler::update)
        DELETE("/employees/{id}", employeeHandler::delete)
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
    fun coffeeRepository(factory: R2dbcRepositoryFactory): EmployeeRepository =
            factory.getRepository(EmployeeRepository::class.java)
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

    fun new(request: ServerRequest) = request
            .bodyToMono(EmployeeRequest::class.java)
            .map { Employee(name = it.name) }
            .flatMap { employeeRepository.save(it) }
            .flatMap { created(URI.create("/api/user/${it.id}")).syncBody(it) }

    fun update(request: ServerRequest) = request
            .bodyToMono(EmployeeRequest::class.java)
            .zipWith(employeeRepository.findById(request.pathVariable("id").toLong()))
            .map { Employee(it.t2.id, it.t1.name) }
            .flatMap { employeeRepository.save(it) }
            .flatMap { ok().syncBody(it) }
            .switchIfEmpty(notFound().build())

    fun delete(request: ServerRequest) = employeeRepository
            .findById(request.pathVariable("id").toLong())
            .flatMap { employeeRepository.delete(it).then(noContent().build()) }
            .switchIfEmpty(notFound().build())
}

interface EmployeeRepository : ReactiveCrudRepository<Employee, Long>

data class EmployeeRequest(@NotBlank val name: String)

data class Employee(@Id val id: Long? = null, @NotBlank val name: String)