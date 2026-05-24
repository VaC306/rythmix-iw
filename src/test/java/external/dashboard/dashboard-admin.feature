Feature: Tests relacionados al dashboard

  Background:
    * url baseUrl

  Scenario: El admin puede acceder a la vista de dashboard
    Given path '/login'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path '/login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method POST

    Given path '/admin/dashboard'
    When method GET
    Then status 200
    * print '>>> Admin puede acceder al dashboard'

  Scenario: El usuario normal no puede acceder al dashboard
    Given path '/login'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path '/login'
    And form field username = 'b'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method POST

    Given path '/admin/dashboard'
    When method GET
    Then status 403
    * print '>>> Usuario normal bloqueado del dashboard'