Feature: Gartic - Juego completo de gartic como admi

  Background:
    * url baseUrl

  Scenario: Se crea el user de admin y juega la partida

    # Iniciamos sesión como usuario
    * configure driver = { type: 'chrome', showDriverLog: false }
    * driver baseUrl + '/login'
    * input('#username', 'a')
    * input('#password', 'aa')
    * submit().click(".form-signin button[type=submit]")
    * match driver.url contains '/admin'

    # Navegamos al juego y creamos la partida
    * driver baseUrl + '/lobby/gartic'
    * submit().click("form[action*='create'] button[type=submit]")
    * waitForUrl(baseUrl + '/gartic')
    * def lobbyCode = script("document.documentElement.dataset.lobbyCode")
    * print '>>> Creado el juego con lobby code:', lobbyCode
    * waitFor('.card-header')
    * screenshot()

    # Iniciamos el juego mediante un petición fetch por comodidad
    * script("fetch('/api/gartic/lobby/' + lobbyCode + '/start', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': config.csrf.value }, body: JSON.stringify({ totalRounds: 4, roundInstruments: [2, 35, 2, 35] }) })")
    * delay(3000)

    # RONDA 1
    * waitFor('#piano-roll')
    * screenshot()
    * click('#tmpSaveButton')
    * delay(2000)

    # RONDA 2
    * waitFor('#piano-roll')
    * screenshot()
    * click('#tmpSaveButton')
    * delay(2000)

    # RONDA 3    
    * waitFor('#piano-roll')
    * screenshot()
    * click('#tmpSaveButton')
    * delay(2000)
    
    # RONDA 4    
    * waitFor('#piano-roll')
    * screenshot()
    * click('#tmpSaveButton')
    * delay(2000)

    # Comprobamos que estamos en el final de partida
    * waitFor('#end-screen-cards-container')
    * screenshot()
    * print '>>> Partida completada del juego gartic de forma exitosa'