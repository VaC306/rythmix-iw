Feature: helpers de autenticacion

  Scenario: login real con csrf
    Given url baseUrl
    And path 'login'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * match csrf != null

    Given path 'login'
    And form field username = username
    And form field password = password
    And form field _csrf = csrf
    When method POST
    Then status 302
