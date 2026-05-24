Feature: Daily Game - Flujo completo por API/HTML

  Background:
    * url baseUrl

  Scenario: un usuario autenticado puede acceder al daily del dia y ver cancion/capa validas

    # Iniciamos sesion como usuario normal
    * configure followRedirects = false
    * def username = 'b'
    * def password = 'aa'
    * call read('helpers/auth.feature')

    # Entramos al daily y comprobamos que hay formulario y reproductor
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'name="answer"'
    And match response contains 'name="songId"'
    And match response contains 'id="player"'

    * def dailySongId = karate.extract(response, 'name="songId" value="(\\d+)"', 1)
    * def layerId = karate.extract(response, 'id="player"[^>]*src="[^"]*/song-layer/(\\d+)/audio"', 1)
    * match dailySongId != null
    * match layerId != null

    # Validamos que la capa actual devuelve audio real
    Given path 'song-layer', layerId, 'audio'
    When method GET
    Then status 200
    And match header Content-Type contains 'audio/mpeg'

  Scenario: no se puede jugar sin iniciar sesion

    # Intento de submit sin login
    * configure followRedirects = false

    Given path 'guess', 'submit'
    And form field answer = '1'
    When method POST
    Then status 302
    And match header Location contains '/login'

    # Intento de navegacion de capas sin login
    Given path 'guess', 'nav'
    And form field dir = 'next'
    When method POST
    Then status 302
    And match header Location contains '/login'

  Scenario: respuesta correcta registra acierto

    # Login del usuario de prueba
    * configure followRedirects = false
    * def username = 'c'
    * def password = 'aa'
    * call read('helpers/auth.feature')

    # Cargamos daily y extraemos csrf + song correcta
    Given path 'guess'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * def dailySongId = karate.extract(response, 'name="songId" value="(\\d+)"', 1)

    Given path 'guess', 'submit'
    And form field answer = dailySongId
    And form field _csrf = csrf
    When method POST
    Then status 302
    And match header Location contains '/guess'

    # Verificamos estado final de exito
    * configure followRedirects = true
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'Correcto!'
    And match response contains 'Daily completado'

  Scenario: respuesta incorrecta consume intento y no permite superar el maximo

    # Login del usuario de prueba
    * configure followRedirects = false
    * def username = 'd'
    * def password = 'aa'
    * call read('helpers/auth.feature')

    # Extraemos csrf y calculamos una respuesta incorrecta determinista
    Given path 'guess'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * def dailySongId = karate.extract(response, 'name="songId" value="(\\d+)"', 1)
    * def wrongAnswer = dailySongId == '1' ? '2' : '1'

    Given path 'guess', 'submit'
    And form field answer = wrongAnswer
    And form field _csrf = csrf
    When method POST
    Then status 302

    # Primer fallo: debe consumir intento
    * configure followRedirects = true
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'Fallaste'

    # Segundo fallo: agota intentos
    * def csrf2 = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * configure followRedirects = false
    Given path 'guess', 'submit'
    And form field answer = wrongAnswer
    And form field _csrf = csrf2
    When method POST
    Then status 302

    * configure followRedirects = true
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'Sin intentos'
    And match response contains 'Daily terminado por intentos'

    # Tercer intento: debe estar bloqueado
    * def csrf3 = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * configure followRedirects = false
    Given path 'guess', 'submit'
    And form field answer = wrongAnswer
    And form field _csrf = csrf3
    When method POST
    Then status 302

    * configure followRedirects = true
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'Ya terminaste el daily de hoy'

  Scenario: no se puede manipular ids desde cliente para hacer trampas

    # Login del usuario de prueba
    * configure followRedirects = false
    * def username = 'e'
    * def password = 'aa'
    * call read('helpers/auth.feature')

    # Extraemos datos reales del daily
    Given path 'guess'
    When method GET
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]+)"', 1)
    * def dailySongId = karate.extract(response, 'name="songId" value="(\\d+)"', 1)
    * def wrongAnswer = dailySongId == '1' ? '2' : '1'

    # Enviamos ids manipulados: el backend debe ignorarlos
    Given path 'guess', 'submit'
    And form field answer = wrongAnswer
    And form field songId = dailySongId
    And form field dailyGameId = '999999'
    And form field userId = '1'
    And form field _csrf = csrf
    When method POST
    Then status 302

    # Debe contar como fallo normal, no como acierto por trampa
    * configure followRedirects = true
    Given path 'guess'
    When method GET
    Then status 200
    And match response contains 'Fallaste'

  Scenario: si no existe daily jugable hoy la respuesta es controlada y sin 500

    # El endpoint /guess nunca debe romper con 500
    Given path 'guess'
    When method GET
    Then status 200
