package eu.wojciechzurek.reactiver2dbcpostgresexample

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EmployeesIntegrationTests {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `when get all users then correct users`(){

        val user = Employee(1, "Wojtek")
        val usersFlux = Flux.just(user)

        //BDDMockito.given("").willReturn(usersFlux)

        client.get()
                .uri("/employees")
                .exchange()
                .expectStatus()
                .isOk
                .expectBodyList(Employee::class.java)
                .hasSize(1)
                .contains()
    }
}