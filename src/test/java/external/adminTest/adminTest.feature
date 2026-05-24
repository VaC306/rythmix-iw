Feature: Tests relacionados a funcionalidades del admin

  Background:
    * url baseUrl

  Scenario: El admin puede banear al usuario con id 2
    Given path '/login'
    When method GET
    Then status 200
    * def loginCsrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path '/login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = loginCsrf
    When method POST
    * def adminCsrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path '/admin/toggle/2'
    And header X-CSRF-TOKEN = adminCsrf
    When method POST
    Then status 200
    * print '>>> Se ha baneado al usuario con id 2'
    * match response contains { enabled: '#boolean' }
    * print '>>> Admin ha cambiado el estado del user 2'

    Given path '/login'
    When method GET
    Then status 200
    * def loginCsrf2 = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path '/login'
    And form field username = 'b'
    And form field password = 'aa'
    And form field _csrf = loginCsrf2
    When method POST
    * print '>>> El usuario baneado con id 2 se intenta loguear, status:', responseStatus

