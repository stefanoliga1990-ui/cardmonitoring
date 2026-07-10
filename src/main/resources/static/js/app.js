(() => {
    "use strict";

    const LANGUAGES = [
        ["it", "Italiano"],
        ["en", "Inglese"],
        ["fr", "Francese"],
        ["de", "Tedesco"],
        ["es", "Spagnolo"],
        ["pt", "Portoghese"],
        ["nl", "Olandese"],
        ["ru", "Russo"],
        ["pl", "Polacco"],
        ["sv", "Svedese"],
        ["kr", "Coreano"],
        ["jp", "Giapponese"],
        ["zh-CN", "Cinese semplificato"],
        ["zh-TW", "Cinese tradizionale"],
        ["id", "Indonesiano"],
        ["th", "Thailandese"]
    ];

    const CONDITIONS = [
        "Mint",
        "Near Mint",
        "Slightly Played",
        "Moderately Played",
        "Played",
        "Poor"
    ];

    const VARIANT_LABELS = [
        ["firstEdition", "Prima edizione"],
        ["reverse", "Reverse"],
        ["graded", "Gradata"],
        ["signed", "Firmata"],
        ["altered", "Alterata"]
    ];

    const state = {
        step: 1,
        expansions: [],
        blueprints: [],
        expansionId: null,
        blueprintId: null,
        language: "",
        condition: "",
        variants: {
            firstEdition: false,
            reverse: false,
            graded: false,
            signed: false,
            altered: false
        },
        loadingExpansions: false,
        loadingBlueprints: false,
        submitting: false,
        activating: false,
        calculation: null,
        blueprintRequest: 0,
        expansionsLoaded: false
    };

    const dashboardState = {
        items: [],
        selectedMonitoringId: null,
        chart: null,
        loading: false
    };

    const authState = {
        user: null,
        csrfHeader: "X-XSRF-TOKEN",
        csrfToken: null,
        submitting: false
    };

    const telegramState = {
        loading: false,
        linked: false,
        linkPollTimer: null,
        linkPollAttempts: 0
    };

    const ROUTES = {
        dashboard: "#dashboard",
        wizard: "#wizard"
    };

    const elements = {
        authView: document.querySelector("#authView"),
        authenticatedNavigation: document.querySelector("#authenticatedNavigation"),
        userMenu: document.querySelector("#userMenu"),
        currentUsername: document.querySelector("#currentUsername"),
        logout: document.querySelector("#logoutButton"),
        authStatus: document.querySelector("#authStatus"),
        loginForm: document.querySelector("#loginForm"),
        registerForm: document.querySelector("#registerForm"),
        showLogin: document.querySelector("#showLoginButton"),
        showRegister: document.querySelector("#showRegisterButton"),
        form: document.querySelector("#monitoringWizard"),
        stepper: document.querySelector(".stepper"),
        steps: [...document.querySelectorAll("[data-step]")],
        indicators: [...document.querySelectorAll("[data-step-indicator]")],
        status: document.querySelector("#globalStatus"),
        expansionSelect: document.querySelector("#expansionSelect"),
        blueprintSelect: document.querySelector("#blueprintSelect"),
        languageSelect: document.querySelector("#languageSelect"),
        conditionChoices: document.querySelector("#conditionChoices"),
        retryExpansions: document.querySelector("#retryExpansionsButton"),
        retryBlueprints: document.querySelector("#retryBlueprintsButton"),
        back: document.querySelector("#backButton"),
        next: document.querySelector("#nextButton"),
        confirm: document.querySelector("#confirmButton"),
        calculationPanel: document.querySelector("#calculationPanel"),
        calculationStatus: document.querySelector("#calculationStatus"),
        calculationCard: document.querySelector("#calculationCard"),
        calculationImage: document.querySelector("#calculationImage"),
        calculationPriceResult: document.querySelector("#calculationPriceResult"),
        calculationNoOffers: document.querySelector("#calculationNoOffers"),
        calculationAveragePrice: document.querySelector("#calculationAveragePrice"),
        calculationSampleInfo: document.querySelector("#calculationSampleInfo"),
        calculationRange: document.querySelector("#calculationRange"),
        calculationConfidence: document.querySelector("#calculationConfidence"),
        calculationTime: document.querySelector("#calculationTime"),
        calculationOffers: document.querySelector("#calculationOffers"),
        editCalculation: document.querySelector("#editCalculationButton"),
        activateMonitoring: document.querySelector("#activateMonitoringButton"),
        successPanel: document.querySelector("#successPanel"),
        successCard: document.querySelector("#successCard"),
        successImage: document.querySelector("#successImage"),
        priceResult: document.querySelector("#priceResult"),
        noOffersResult: document.querySelector("#noOffersResult"),
        averagePrice: document.querySelector("#averagePrice"),
        sampleInfo: document.querySelector("#sampleInfo"),
        createAnother: document.querySelector("#createAnotherButton"),
        goToDashboard: document.querySelector("#goToDashboardButton"),
        dashboardView: document.querySelector("#dashboardView"),
        wizardView: document.querySelector("#wizardView"),
        detailView: document.querySelector("#detailView"),
        dashboardStatus: document.querySelector("#dashboardStatus"),
        telegramPanel: document.querySelector("#telegramPanel"),
        telegramStatus: document.querySelector("#telegramStatus"),
        telegramCreateLink: document.querySelector("#telegramCreateLinkButton"),
        telegramTest: document.querySelector("#telegramTestButton"),
        telegramUnlink: document.querySelector("#telegramUnlinkButton"),
        telegramQrBox: document.querySelector("#telegramQrBox"),
        telegramQrCode: document.querySelector("#telegramQrCode"),
        telegramOpenLink: document.querySelector("#telegramOpenLink"),
        telegramQrExpiry: document.querySelector("#telegramQrExpiry"),
        detailStatus: document.querySelector("#detailStatus"),
        dashboardLoading: document.querySelector("#dashboardLoading"),
        dashboardEmpty: document.querySelector("#dashboardEmpty"),
        monitoringGrid: document.querySelector("#monitoringGrid"),
        newMonitoring: document.querySelector("#newMonitoringButton"),
        backToDashboard: document.querySelector("#backToDashboardButton"),
        detailRefresh: document.querySelector("#detailRefreshButton"),
        detailDeactivate: document.querySelector("#detailDeactivateButton"),
        detailImage: document.querySelector("#detailImage"),
        loadingOverlay: document.querySelector("#loadingOverlay"),
        loadingOverlayTitle: document.querySelector("#loadingOverlayTitle"),
        loadingOverlayMessage: document.querySelector("#loadingOverlayMessage")
    };

    initialize();

    async function initialize() {
        renderLanguages();
        renderConditions();
        bindEvents();
        renderStep();
        await initializeSession();
    }

    function bindEvents() {
        elements.loginForm.addEventListener("submit", submitLogin);
        elements.registerForm.addEventListener("submit", submitRegistration);
        elements.showLogin.addEventListener("click", () => switchAuthMode("login"));
        elements.showRegister.addEventListener("click", () => switchAuthMode("register"));
        elements.logout.addEventListener("click", logout);
        elements.expansionSelect.addEventListener("change", handleExpansionChange);
        elements.blueprintSelect.addEventListener("change", () => {
            state.blueprintId = numberOrNull(elements.blueprintSelect.value);
            updateActions();
        });
        elements.languageSelect.addEventListener("change", () => {
            state.language = elements.languageSelect.value;
            updateActions();
        });
        elements.conditionChoices.addEventListener("change", (event) => {
            if (event.target.matches('input[name="condition"]')) {
                state.condition = event.target.value;
                updateActions();
            }
        });
        VARIANT_LABELS.forEach(([property]) => {
            const input = document.querySelector(`#${property}Input`);
            input.addEventListener("change", () => {
                state.variants[property] = input.checked;
            });
        });
        elements.back.addEventListener("click", goBack);
        elements.next.addEventListener("click", goNext);
        elements.form.addEventListener("submit", submitCalculation);
        elements.retryExpansions.addEventListener("click", loadExpansions);
        elements.retryBlueprints.addEventListener("click", () => {
            if (state.expansionId !== null) {
                loadBlueprints(state.expansionId);
            }
        });
        elements.createAnother.addEventListener("click", resetWizard);
        elements.editCalculation.addEventListener("click", editCalculation);
        elements.activateMonitoring.addEventListener("click", activateMonitoring);
        elements.goToDashboard.addEventListener("click", showDashboard);
        elements.newMonitoring.addEventListener("click", showWizard);
        elements.telegramCreateLink.addEventListener("click", createTelegramLink);
        elements.telegramTest.addEventListener("click", sendTelegramTestMessage);
        elements.telegramUnlink.addEventListener("click", unlinkTelegram);
        elements.backToDashboard.addEventListener("click", showDashboard);
        elements.detailRefresh.addEventListener("click", () => refreshMonitoring(dashboardState.selectedMonitoringId, true));
        elements.detailDeactivate.addEventListener("click", () => deactivateMonitoring(dashboardState.selectedMonitoringId));
        elements.monitoringGrid.addEventListener("click", handleMonitoringGridClick);
        window.addEventListener("popstate", () => {
            if (authState.user !== null) {
                showCurrentRoute();
            }
        });
        window.addEventListener("hashchange", () => {
            if (authState.user !== null) {
                showCurrentRoute();
            }
        });
        document.querySelectorAll("[data-view-link]").forEach((link) => {
            link.addEventListener("click", (event) => {
                event.preventDefault();
                if (link.dataset.viewLink === "wizard") {
                    showWizard();
                }
                else {
                    showDashboard();
                }
            });
        });
    }

    async function initializeSession() {
        try {
            await refreshCsrfToken();
            const user = await requestJson("/api/auth/me");
            await completeAuthentication(user);
        }
        catch (error) {
            const message = error.status === 401
                ? ""
                : errorMessage(error, "Impossibile verificare la sessione.");
            showAuth(message);
        }
    }

    async function refreshCsrfToken() {
        const csrf = await requestJson("/api/auth/csrf");
        authState.csrfHeader = csrf.headerName;
        authState.csrfToken = csrf.token;
    }

    async function submitLogin(event) {
        event.preventDefault();
        if (authState.submitting || !elements.loginForm.reportValidity()) {
            return;
        }
        const data = new FormData(elements.loginForm);
        await submitCredentials("/api/auth/login", {
            username: data.get("username"),
            password: data.get("password")
        }, "Accesso in corso…");
    }

    async function submitRegistration(event) {
        event.preventDefault();
        if (authState.submitting || !elements.registerForm.reportValidity()) {
            return;
        }
        const password = document.querySelector("#registerPassword").value;
        const confirmation = document.querySelector("#registerPasswordConfirmation").value;
        if (password !== confirmation) {
            setAuthStatus("Le password non coincidono.", "error");
            return;
        }
        const data = new FormData(elements.registerForm);
        await submitCredentials("/api/auth/register", {
            username: data.get("username"),
            password
        }, "Creazione dell’account…");
    }

    async function submitCredentials(url, credentials, loadingMessage) {
        authState.submitting = true;
        setAuthFormsDisabled(true);
        setAuthStatus(loadingMessage, "info");
        try {
            const user = await requestJson(url, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(credentials)
            });
            await completeAuthentication(user);
        }
        catch (error) {
            setAuthStatus(errorMessage(error, "Operazione non riuscita."), "error");
        }
        finally {
            authState.submitting = false;
            setAuthFormsDisabled(false);
        }
    }

    async function completeAuthentication(user) {
        authState.user = user;
        elements.currentUsername.textContent = user.username;
        elements.authenticatedNavigation.hidden = false;
        elements.userMenu.hidden = false;
        elements.loginForm.reset();
        elements.registerForm.reset();
        clearPanelStatus(elements.authStatus);
        await refreshCsrfToken();
        await showCurrentRoute();
    }

    function switchAuthMode(mode) {
        const login = mode === "login";
        elements.loginForm.hidden = !login;
        elements.registerForm.hidden = login;
        elements.showLogin.classList.toggle("is-active", login);
        elements.showRegister.classList.toggle("is-active", !login);
        elements.showLogin.setAttribute("aria-selected", String(login));
        elements.showRegister.setAttribute("aria-selected", String(!login));
        clearPanelStatus(elements.authStatus);
        (login ? document.querySelector("#loginUsername") : document.querySelector("#registerUsername")).focus();
    }

    function setAuthFormsDisabled(disabled) {
        [...elements.loginForm.elements, ...elements.registerForm.elements, elements.showLogin, elements.showRegister]
            .forEach((element) => { element.disabled = disabled; });
    }

    async function logout() {
        if (authState.submitting) {
            return;
        }
        authState.submitting = true;
        elements.logout.disabled = true;
        try {
            await requestJson("/api/auth/logout", { method: "POST" });
            authState.user = null;
            authState.csrfToken = null;
            dashboardState.items = [];
            telegramState.linked = false;
            stopTelegramLinkPolling();
            await refreshCsrfToken();
            showAuth("Sessione terminata correttamente.", "info");
        }
        catch (error) {
            setDashboardStatus(errorMessage(error, "Logout non riuscito."), "error");
        }
        finally {
            authState.submitting = false;
            elements.logout.disabled = false;
        }
    }

    function showAuth(message, type = "error") {
        authState.user = null;
        elements.authenticatedNavigation.hidden = true;
        elements.userMenu.hidden = true;
        setActiveView("auth");
        switchAuthMode("login");
        if (message) {
            setAuthStatus(message, type);
        }
    }

    function setAuthStatus(message, type) {
        setPanelStatus(elements.authStatus, message, type);
    }

    function renderLanguages() {
        LANGUAGES.forEach(([code, label]) => {
            elements.languageSelect.add(new Option(label, code));
        });
    }

    function renderConditions() {
        CONDITIONS.forEach((condition, index) => {
            const input = document.createElement("input");
            input.type = "radio";
            input.name = "condition";
            input.id = `condition-${index}`;
            input.value = condition;
            input.required = true;

            const text = document.createElement("span");
            text.textContent = condition;

            const label = document.createElement("label");
            label.className = "choice-card";
            label.htmlFor = input.id;
            label.append(input, text);
            elements.conditionChoices.append(label);
        });
    }

    async function loadExpansions() {
        state.loadingExpansions = true;
        state.expansionsLoaded = false;
        state.expansions = [];
        state.expansionId = null;
        state.blueprintId = null;
        elements.retryExpansions.hidden = true;
        setSelectState(elements.expansionSelect, "Caricamento dei set…", true);
        resetBlueprintSelect("Seleziona prima un set");
        setStatus("Caricamento dei set Pokémon da CardTrader…", "info");
        updateActions();

        try {
            const expansions = await requestJson("/api/catalog/expansions");
            state.expansions = Array.isArray(expansions) ? expansions : [];
            state.expansionsLoaded = true;
            populateExpansionSelect();
            if (state.expansions.length === 0) {
                setStatus("Nessun set Pokémon disponibile al momento.", "error");
            }
            else {
                clearStatus();
            }
        }
        catch (error) {
            setSelectState(elements.expansionSelect, "Impossibile caricare i set", true);
            elements.retryExpansions.hidden = false;
            setStatus(errorMessage(error, "Impossibile caricare i set Pokémon."), "error");
        }
        finally {
            state.loadingExpansions = false;
            updateActions();
        }
    }

    function populateExpansionSelect() {
        elements.expansionSelect.replaceChildren(new Option("Seleziona un set", ""));
        state.expansions.forEach((expansion) => {
            const code = expansion.code ? ` · ${expansion.code}` : "";
            elements.expansionSelect.add(new Option(`${expansion.name}${code}`, String(expansion.id)));
        });
        elements.expansionSelect.disabled = state.expansions.length === 0;
    }

    function handleExpansionChange() {
        state.expansionId = numberOrNull(elements.expansionSelect.value);
        state.blueprintId = null;
        state.blueprints = [];
        clearStatus();
        if (state.expansionId === null) {
            state.blueprintRequest += 1;
            state.loadingBlueprints = false;
            resetBlueprintSelect("Seleziona prima un set");
            updateActions();
            return;
        }
        loadBlueprints(state.expansionId);
    }

    async function loadBlueprints(expansionId) {
        const requestId = ++state.blueprintRequest;
        state.loadingBlueprints = true;
        state.blueprintId = null;
        state.blueprints = [];
        elements.retryBlueprints.hidden = true;
        setSelectState(elements.blueprintSelect, "Caricamento delle carte…", true);
        setStatus("Caricamento delle carte del set selezionato…", "info");
        updateActions();

        try {
            const blueprints = await requestJson(`/api/catalog/expansions/${expansionId}/blueprints`);
            if (requestId !== state.blueprintRequest || expansionId !== state.expansionId) {
                return;
            }
            state.blueprints = Array.isArray(blueprints) ? blueprints : [];
            populateBlueprintSelect();
            if (state.blueprints.length === 0) {
                setStatus("Nessuna carta disponibile per questo set.", "error");
            }
            else {
                clearStatus();
            }
        }
        catch (error) {
            if (requestId !== state.blueprintRequest || expansionId !== state.expansionId) {
                return;
            }
            setSelectState(elements.blueprintSelect, "Impossibile caricare le carte", true);
            elements.retryBlueprints.hidden = false;
            setStatus(errorMessage(error, "Impossibile caricare le carte del set."), "error");
        }
        finally {
            if (requestId === state.blueprintRequest) {
                state.loadingBlueprints = false;
                updateActions();
            }
        }
    }

    function populateBlueprintSelect() {
        elements.blueprintSelect.replaceChildren(new Option("Seleziona una carta", ""));
        state.blueprints.forEach((blueprint) => {
            const version = blueprint.version ? ` · ${blueprint.version}` : "";
            elements.blueprintSelect.add(new Option(`${blueprint.name}${version}`, String(blueprint.id)));
        });
        elements.blueprintSelect.disabled = state.blueprints.length === 0;
    }

    function resetBlueprintSelect(message) {
        setSelectState(elements.blueprintSelect, message, true);
        elements.retryBlueprints.hidden = true;
    }

    function setSelectState(select, message, disabled) {
        select.replaceChildren(new Option(message, ""));
        select.disabled = disabled;
    }

    function goNext() {
        if (!isStepValid(state.step)) {
            setStatus(validationMessage(state.step), "error");
            return;
        }
        clearStatus();
        if (state.step < 6) {
            state.step += 1;
            renderStep();
        }
    }

    function goBack() {
        if (state.step > 1 && !state.submitting) {
            state.step -= 1;
            clearStatus();
            renderStep();
        }
    }

    function renderStep() {
        elements.steps.forEach((step) => {
            const stepNumber = Number(step.dataset.step);
            const visible = stepNumber === state.step;
            step.hidden = !visible;
            step.classList.toggle("is-visible", visible);
        });

        elements.indicators.forEach((indicator) => {
            const stepNumber = Number(indicator.dataset.stepIndicator);
            indicator.classList.toggle("is-active", stepNumber === state.step);
            indicator.classList.toggle("is-complete", stepNumber < state.step);
            if (stepNumber === state.step) {
                indicator.setAttribute("aria-current", "step");
            }
            else {
                indicator.removeAttribute("aria-current");
            }
        });

        if (state.step === 6) {
            renderSummary();
        }
        updateActions();
    }

    function updateActions() {
        elements.back.hidden = state.step === 1;
        elements.next.hidden = state.step === 6;
        elements.confirm.hidden = state.step !== 6;
        elements.next.disabled = !isStepValid(state.step) || isLoadingForStep(state.step);
        elements.confirm.disabled = state.submitting || !allSelectionsValid();
    }

    function isStepValid(step) {
        switch (step) {
            case 1:
                return selectedExpansion() !== undefined;
            case 2:
                return selectedBlueprint() !== undefined;
            case 3:
                return LANGUAGES.some(([code]) => code === state.language);
            case 4:
                return CONDITIONS.includes(state.condition);
            case 5:
                return true;
            case 6:
                return allSelectionsValid();
            default:
                return false;
        }
    }

    function allSelectionsValid() {
        return [1, 2, 3, 4, 5].every(isStepValid);
    }

    function isLoadingForStep(step) {
        return (step === 1 && (state.loadingExpansions || state.loadingBlueprints))
            || (step === 2 && state.loadingBlueprints);
    }

    function validationMessage(step) {
        if (isLoadingForStep(step)) {
            return "Attendi il completamento del caricamento.";
        }
        return {
            1: "Seleziona un set per continuare.",
            2: "Seleziona una carta per continuare.",
            3: "Seleziona la lingua della carta.",
            4: "Seleziona la condizione della carta.",
            6: "Completa tutte le selezioni prima di confermare."
        }[step] || "Completa la selezione per continuare.";
    }

    function renderSummary() {
        const expansion = selectedExpansion();
        const blueprint = selectedBlueprint();
        const language = LANGUAGES.find(([code]) => code === state.language);
        document.querySelector("#summaryExpansion").textContent = expansion
            ? `${expansion.name}${expansion.code ? ` (${expansion.code})` : ""}`
            : "—";
        document.querySelector("#summaryBlueprint").textContent = blueprint
            ? `${blueprint.name}${blueprint.version ? ` · ${blueprint.version}` : ""}`
            : "—";
        document.querySelector("#summaryLanguage").textContent = language ? language[1] : "—";
        document.querySelector("#summaryCondition").textContent = state.condition || "—";
        const activeVariants = VARIANT_LABELS
            .filter(([property]) => state.variants[property])
            .map(([, label]) => label);
        document.querySelector("#summaryVariants").textContent = activeVariants.length > 0
            ? activeVariants.join(", ")
            : "Standard";
    }

    async function submitCalculation(event) {
        event.preventDefault();
        if (!allSelectionsValid() || state.submitting) {
            setStatus(validationMessage(6), "error");
            return;
        }

        state.submitting = true;
        elements.confirm.textContent = "Calcolo in corso…";
        setStatus("Stiamo verificando le offerte compatibili su CardTrader.", "info");
        showLoadingOverlay(
            "Calcolo prezzo medio",
            "Interroghiamo CardTrader e cerchiamo l'immagine della carta. Potrebbe richiedere qualche secondo.");
        updateActions();

        try {
            const calculation = await requestJson("/api/price-calculations", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(criteriaPayload())
            });
            showCalculation(calculation);
        }
        catch (error) {
            setStatus(errorMessage(error, "Non è stato possibile calcolare il prezzo medio."), "error");
        }
        finally {
            state.submitting = false;
            elements.confirm.textContent = "Calcola prezzo medio";
            hideLoadingOverlay();
            updateActions();
        }
    }

    function criteriaPayload() {
        return {
            expansionId: state.expansionId,
            blueprintId: state.blueprintId,
            language: state.language,
            condition: state.condition,
            ...state.variants
        };
    }

    function showCalculation(calculation) {
        state.calculation = calculation;
        elements.form.hidden = true;
        elements.stepper.hidden = true;
        elements.successPanel.hidden = true;
        clearStatus();
        clearPanelStatus(elements.calculationStatus);
        elements.calculationPanel.hidden = false;
        elements.calculationCard.textContent = `${calculation.cardName} · ${calculation.cardVersion}`;
        renderCardImage(elements.calculationImage, calculation, "large");
        elements.calculationTime.textContent = formatDateTime(calculation.calculatedAt);
        elements.calculationConfidence.textContent = confidenceLabel(calculation.confidence);

        const hasOffers = calculation.confidence !== "NO_DATA" && calculation.averagePriceCents !== null;
        elements.calculationPriceResult.hidden = !hasOffers;
        elements.calculationNoOffers.hidden = hasOffers;
        if (hasOffers) {
            elements.calculationAveragePrice.textContent = formatCents(
                calculation.averagePriceCents, calculation.currency);
            elements.calculationSampleInfo.textContent = sampleDescription(calculation);
            elements.calculationRange.textContent = `${formatCents(calculation.minimumPriceCents, calculation.currency)} – ${formatCents(calculation.maximumPriceCents, calculation.currency)}`;
            renderUsedOffers(calculation.offersUsed, calculation.currency);
        }
        else {
            elements.calculationOffers.replaceChildren();
        }
        document.querySelector("#calculationTitle").focus({ preventScroll: true });
    }

    function renderUsedOffers(offers, currency) {
        elements.calculationOffers.replaceChildren();
        (Array.isArray(offers) ? offers : []).forEach((offer) => {
            const item = document.createElement("li");
            item.append(
                createElement("strong", "", formatCents(offer.priceCents, offer.currency || currency)),
                createElement("span", "", `Inserzione CardTrader #${offer.offerId} · quantità ${offer.quantity}`)
            );
            elements.calculationOffers.append(item);
        });
    }

    function editCalculation() {
        if (state.activating) {
            return;
        }
        state.calculation = null;
        elements.calculationPanel.hidden = true;
        elements.form.hidden = false;
        elements.stepper.hidden = false;
        state.step = 6;
        renderStep();
        document.querySelector("#step-6-title").focus({ preventScroll: true });
    }

    async function activateMonitoring() {
        if (state.calculation === null || state.activating) {
            return;
        }
        state.activating = true;
        elements.editCalculation.disabled = true;
        elements.activateMonitoring.disabled = true;
        elements.activateMonitoring.textContent = "Attivazione…";
        showLoadingOverlay(
            "Attivazione monitoraggio",
            "Ricalcoliamo il prezzo, recuperiamo l'immagine e salviamo la prima rilevazione.");
        setPanelStatus(
            elements.calculationStatus,
            "Nuova verifica delle offerte e attivazione del monitoraggio in corso…",
            "info");

        try {
            const created = await requestJson("/api/monitorings", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(criteriaPayload())
            });
            showSuccess(created);
        }
        catch (error) {
            setPanelStatus(
                elements.calculationStatus,
                errorMessage(error, "Non è stato possibile attivare il monitoraggio."),
                "error");
        }
        finally {
            state.activating = false;
            elements.editCalculation.disabled = false;
            elements.activateMonitoring.disabled = false;
            elements.activateMonitoring.textContent = "Attiva monitoraggio";
            hideLoadingOverlay();
        }
    }

    function showLoadingOverlay(title, message) {
        elements.loadingOverlayTitle.textContent = title;
        elements.loadingOverlayMessage.textContent = message;
        elements.loadingOverlay.hidden = false;
        document.body.classList.add("is-loading-overlay-visible");
    }

    function hideLoadingOverlay() {
        elements.loadingOverlay.hidden = true;
        document.body.classList.remove("is-loading-overlay-visible");
    }

    function showSuccess(created) {
        const monitoring = created.monitoring;
        const observation = created.initialObservation;
        elements.form.hidden = true;
        elements.stepper.hidden = true;
        elements.calculationPanel.hidden = true;
        clearStatus();
        elements.successPanel.hidden = false;
        elements.successCard.textContent = `${monitoring.cardName} · ${monitoring.cardVersion}`;

        renderCardImage(elements.successImage, monitoring, "large");

        const hasOffers = observation.confidence !== "NO_DATA" && observation.averagePriceCents !== null;
        elements.priceResult.hidden = !hasOffers;
        elements.noOffersResult.hidden = hasOffers;
        if (hasOffers) {
            elements.averagePrice.textContent = formatCents(observation.averagePriceCents, observation.currency);
            elements.sampleInfo.textContent = sampleDescription(observation);
        }
        document.querySelector("#successTitle").focus({ preventScroll: true });
    }

    function sampleDescription(observation) {
        const confidence = {
            LOW: "bassa",
            MEDIUM: "media",
            HIGH: "alta"
        }[observation.confidence] || observation.confidence.toLowerCase();
        return `${usedOffersLabel(observation.usedOffers)} su ${compatibleOffersLabel(observation.compatibleOffers)} · Attendibilità ${confidence}`;
    }

    function formatCents(cents, currency) {
        return new Intl.NumberFormat("it-IT", {
            style: "currency",
            currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(Number(cents) / 100);
    }

    function resetWizard() {
        elements.form.reset();
        state.step = 1;
        state.blueprints = [];
        state.expansionId = null;
        state.blueprintId = null;
        state.language = "";
        state.condition = "";
        state.calculation = null;
        state.activating = false;
        Object.keys(state.variants).forEach((property) => {
            state.variants[property] = false;
        });
        populateExpansionSelect();
        resetBlueprintSelect("Seleziona prima un set");
        elements.successPanel.hidden = true;
        elements.calculationPanel.hidden = true;
        elements.form.hidden = false;
        elements.stepper.hidden = false;
        clearStatus();
        renderStep();
        elements.expansionSelect.focus();
    }

    async function showCurrentRoute() {
        const route = currentRoute();
        if (route.view === "detail") {
            await showDetail(route.monitoringId, false);
            return;
        }
        if (route.view === "wizard") {
            showWizard(false);
            return;
        }
        await showDashboard(false);
    }

    function currentRoute() {
        const hash = window.location.hash || ROUTES.dashboard;
        const detailMatch = hash.match(/^#monitoring\/(\d+)$/);
        if (detailMatch) {
            return {
                view: "detail",
                monitoringId: Number(detailMatch[1])
            };
        }
        if (hash === ROUTES.wizard) {
            return { view: "wizard" };
        }
        return { view: "dashboard" };
    }

    function updateRoute(hash) {
        if (window.location.hash !== hash) {
            window.history.pushState(null, "", hash);
        }
    }

    async function showDashboard(updateHash = true) {
        if (authState.user === null) {
            showAuth();
            return;
        }
        if (updateHash) {
            updateRoute(ROUTES.dashboard);
        }
        setActiveView("dashboard");
        await loadDashboard();
    }

    function showWizard(updateHash = true) {
        if (authState.user === null) {
            showAuth();
            return;
        }
        if (updateHash) {
            updateRoute(ROUTES.wizard);
        }
        setActiveView("wizard");
        if (!elements.successPanel.hidden || !elements.calculationPanel.hidden) {
            resetWizard();
        }
        if (!state.expansionsLoaded && !state.loadingExpansions) {
            loadExpansions();
        }
    }

    function setActiveView(view) {
        elements.authView.hidden = view !== "auth";
        elements.dashboardView.hidden = view !== "dashboard";
        elements.wizardView.hidden = view !== "wizard";
        elements.detailView.hidden = view !== "detail";
        document.querySelectorAll(".nav-link[data-view-link]").forEach((link) => {
            link.classList.toggle("is-active", link.dataset.viewLink === view);
        });
        if (view !== "detail" && dashboardState.chart !== null) {
            dashboardState.chart.destroy();
            dashboardState.chart = null;
        }
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    async function loadDashboard() {
        dashboardState.loading = true;
        elements.dashboardLoading.hidden = false;
        elements.dashboardEmpty.hidden = true;
        elements.monitoringGrid.hidden = true;
        clearDashboardStatus();
        loadTelegramStatus();

        try {
            const monitorings = await requestJson("/api/monitorings");
            dashboardState.items = await Promise.all(monitorings.map(async (monitoring) => {
                try {
                    const observations = await requestJson(`/api/monitorings/${monitoring.id}/observations`);
                    return { monitoring, observations, historyError: null };
                }
                catch (error) {
                    return { monitoring, observations: [], historyError: errorMessage(error, "Storico non disponibile") };
                }
            }));
            renderDashboard();
        }
        catch (error) {
            elements.dashboardEmpty.hidden = true;
            elements.monitoringGrid.hidden = true;
            setDashboardStatus(errorMessage(error, "Impossibile caricare i monitoraggi."), "error");
        }
        finally {
            dashboardState.loading = false;
            elements.dashboardLoading.hidden = true;
        }
    }

    function renderDashboard() {
        elements.monitoringGrid.replaceChildren();
        if (dashboardState.items.length === 0) {
            elements.dashboardEmpty.hidden = false;
            elements.monitoringGrid.hidden = true;
            return;
        }

        dashboardState.items.forEach((item) => {
            elements.monitoringGrid.append(createMonitoringCard(item));
        });
        elements.dashboardEmpty.hidden = true;
        elements.monitoringGrid.hidden = false;
    }

    async function loadTelegramStatus() {
        if (telegramState.loading) {
            return;
        }
        telegramState.loading = true;
        try {
            const status = await requestJson("/api/telegram/status");
            renderTelegramStatus(status);
        }
        catch (error) {
            elements.telegramPanel.hidden = true;
        }
        finally {
            telegramState.loading = false;
        }
    }

    function renderTelegramStatus(status) {
        if (!status || !status.integrationEnabled) {
            elements.telegramPanel.hidden = true;
            return;
        }
        telegramState.linked = Boolean(status.linked && status.notificationsEnabled);
        elements.telegramPanel.hidden = false;
        const pollingForLink = telegramState.linkPollTimer !== null;
        if (!pollingForLink || telegramState.linked) {
            elements.telegramQrBox.hidden = true;
            elements.telegramQrCode.replaceChildren();
            elements.telegramOpenLink.removeAttribute("href");
        }
        elements.telegramTest.hidden = !telegramState.linked;
        elements.telegramUnlink.hidden = !telegramState.linked;
        elements.telegramCreateLink.textContent = telegramState.linked ? "Ricollega Telegram" : "Collega Telegram";

        if (telegramState.linked) {
            stopTelegramLinkPolling();
            const username = status.username ? `@${status.username}` : "chat privata";
            const linkedAt = status.linkedAt ? ` Collegato il ${formatDateTime(status.linkedAt)}.` : "";
            const error = status.lastError ? ` Ultimo errore: ${status.lastError}` : "";
            setTelegramStatus(`Telegram collegato (${username}).${linkedAt}${error}`, status.lastError ? "error" : "info");
        }
        else {
            setTelegramStatus("Scansiona il QR code con Telegram e premi Start per collegare il bot.", "info");
        }
    }

    async function createTelegramLink() {
        if (telegramState.loading) {
            return;
        }
        telegramState.loading = true;
        setTelegramButtonsDisabled(true);
        setTelegramStatus("Generazione del QR code in corso…", "info");
        try {
            const link = await requestJson("/api/telegram/link-requests", { method: "POST" });
            renderTelegramQr(link);
            setTelegramStatus("Scansiona il QR code oppure apri Telegram da questo dispositivo, poi premi Start nel bot.", "info");
            startTelegramLinkPolling();
        }
        catch (error) {
            setTelegramStatus(errorMessage(error, "Impossibile generare il collegamento Telegram."), "error");
        }
        finally {
            telegramState.loading = false;
            setTelegramButtonsDisabled(false);
        }
    }

    function renderTelegramQr(link) {
        elements.telegramQrBox.hidden = false;
        elements.telegramQrCode.replaceChildren();
        elements.telegramOpenLink.href = link.linkUrl;
        elements.telegramQrExpiry.textContent = link.expiresAt
            ? `QR valido fino alle ${formatDateTime(link.expiresAt)}`
            : "QR temporaneo";

        if (typeof QRCode !== "undefined" && typeof QRCode.toCanvas === "function") {
            const canvas = document.createElement("canvas");
            QRCode.toCanvas(canvas, link.linkUrl, {
                width: 178,
                margin: 1,
                color: {
                    dark: "#17231f",
                    light: "#ffffff"
                }
            }, (error) => {
                if (error) {
                    renderTelegramQrFallback(link.linkUrl);
                    return;
                }
                elements.telegramQrCode.replaceChildren(canvas);
            });
            return;
        }

        renderTelegramQrFallback(link.linkUrl);
    }

    function renderTelegramQrFallback(linkUrl) {
        elements.telegramQrCode.replaceChildren(
            createElement("span", "telegram-qr-fallback", "QR non disponibile")
        );
        elements.telegramOpenLink.href = linkUrl;
    }

    async function sendTelegramTestMessage() {
        if (telegramState.loading) {
            return;
        }
        telegramState.loading = true;
        setTelegramButtonsDisabled(true);
        setTelegramStatus("Invio del messaggio di test…", "info");
        try {
            await requestJson("/api/telegram/test-message", { method: "POST" });
            setTelegramStatus("Messaggio di test inviato. Controlla Telegram.", "info");
        }
        catch (error) {
            setTelegramStatus(errorMessage(error, "Messaggio di test non riuscito."), "error");
        }
        finally {
            telegramState.loading = false;
            setTelegramButtonsDisabled(false);
        }
    }

    async function unlinkTelegram() {
        if (telegramState.loading || !window.confirm("Scollegare Telegram da questo account?")) {
            return;
        }
        telegramState.loading = true;
        setTelegramButtonsDisabled(true);
        try {
            await requestJson("/api/telegram/unlink", { method: "POST" });
            telegramState.loading = false;
            await loadTelegramStatus();
            setTelegramStatus("Telegram scollegato correttamente.", "info");
        }
        catch (error) {
            setTelegramStatus(errorMessage(error, "Scollegamento non riuscito."), "error");
        }
        finally {
            telegramState.loading = false;
            setTelegramButtonsDisabled(false);
        }
    }

    function setTelegramButtonsDisabled(disabled) {
        elements.telegramCreateLink.disabled = disabled;
        elements.telegramTest.disabled = disabled;
        elements.telegramUnlink.disabled = disabled;
    }

    function setTelegramStatus(message, type) {
        elements.telegramStatus.textContent = message;
        elements.telegramStatus.classList.toggle("is-error", type === "error");
    }

    function startTelegramLinkPolling() {
        stopTelegramLinkPolling();
        telegramState.linkPollAttempts = 0;
        const poll = async () => {
            telegramState.linkPollAttempts += 1;
            try {
                const status = await requestJson("/api/telegram/status");
                renderTelegramStatus(status);
                if (status && status.linked && status.notificationsEnabled) {
                    setTelegramStatus("Telegram collegato correttamente. Puoi inviare un messaggio di test.", "info");
                    return;
                }
            }
            catch (error) {
                return;
            }
            if (telegramState.linkPollAttempts < 30) {
                telegramState.linkPollTimer = window.setTimeout(poll, 4000);
            }
        };
        telegramState.linkPollTimer = window.setTimeout(poll, 4000);
    }

    function stopTelegramLinkPolling() {
        if (telegramState.linkPollTimer !== null) {
            window.clearTimeout(telegramState.linkPollTimer);
            telegramState.linkPollTimer = null;
        }
    }

    function createMonitoringCard(item) {
        const monitoring = item.monitoring;
        const latest = latestObservation(item.observations);
        const card = createElement("article", "monitoring-card");
        card.dataset.monitoringId = String(monitoring.id);

        const header = createElement("header", "monitoring-card-header");
        const image = createElement("div", "card-image-preview monitoring-card-image");
        renderCardImage(image, monitoring, "small");
        const identity = document.createElement("div");
        identity.append(
            createElement("p", "monitoring-set", `${monitoring.expansionName} · ${monitoring.expansionCode}`),
            createElement("h2", "", monitoring.cardName),
            createElement("p", "monitoring-version", monitoring.cardVersion)
        );
        const confidence = createElement("span", "confidence-badge", confidenceLabel(latest && latest.confidence));
        confidence.dataset.confidence = latest ? latest.confidence : "NO_DATA";
        header.append(image, identity, confidence);

        const priceRow = createElement("div", "monitoring-price-row");
        const price = createElement("div", "monitoring-price");
        price.append(
            createElement("span", "", "Ultimo prezzo medio"),
            createElement("strong", "", observationPrice(latest, monitoring.currency))
        );
        const updated = createElement(
            "span",
            "monitoring-updated",
            monitoring.lastCheckedAt ? `Aggiornato ${formatDateTime(monitoring.lastCheckedAt)}` : "Mai aggiornato"
        );
        priceRow.append(price, updated);

        const method = createElement(
            "p",
            "monitoring-method",
            latest && latest.usedOffers > 0
                ? `${cheapestOffersLabel(latest.usedOffers)} su ${compatibleOffersLabel(latest.compatibleOffers)}.`
                : "Nessuna offerta compatibile nell’ultima rilevazione."
        );

        const failure = monitoring.lastError || item.historyError;
        const errorPanel = createElement("div", "monitoring-error");
        if (failure) {
            errorPanel.append(
                createElement("strong", "", "Ultimo errore"),
                createElement("span", "", failure)
            );
        }
        else {
            errorPanel.hidden = true;
        }

        const actions = createElement("footer", "monitoring-card-actions");
        actions.append(
            actionButton("Dettaglio e grafico", "detail", monitoring.id, "button button--secondary"),
            actionButton("Aggiorna", "refresh", monitoring.id, "button button--primary"),
            actionButton("Disattiva", "deactivate", monitoring.id, "button button--danger")
        );

        card.append(header, priceRow, method, errorPanel, actions);
        return card;
    }

    function actionButton(label, action, monitoringId, className) {
        const button = createElement("button", className, label);
        button.type = "button";
        button.dataset.action = action;
        button.dataset.monitoringId = String(monitoringId);
        return button;
    }

    function handleMonitoringGridClick(event) {
        const button = event.target.closest("button[data-action]");
        if (!button) {
            return;
        }
        const monitoringId = numberOrNull(button.dataset.monitoringId);
        if (monitoringId === null) {
            return;
        }
        if (button.dataset.action === "detail") {
            showDetail(monitoringId);
        }
        else if (button.dataset.action === "refresh") {
            refreshMonitoring(monitoringId, false, button);
        }
        else if (button.dataset.action === "deactivate") {
            deactivateMonitoring(monitoringId);
        }
    }

    async function showDetail(monitoringId, updateHash = true) {
        dashboardState.selectedMonitoringId = monitoringId;
        if (updateHash) {
            updateRoute(`#monitoring/${monitoringId}`);
        }
        setActiveView("detail");
        elements.detailRefresh.disabled = true;
        elements.detailDeactivate.disabled = true;
        setDetailStatus("Caricamento del dettaglio…", "info");

        try {
            const [monitoring, observations] = await Promise.all([
                requestJson(`/api/monitorings/${monitoringId}`),
                requestJson(`/api/monitorings/${monitoringId}/observations`)
            ]);
            renderDetail(monitoring, observations);
            clearDetailStatus();
        }
        catch (error) {
            setDetailStatus(errorMessage(error, "Impossibile caricare il dettaglio."), "error");
        }
        finally {
            elements.detailRefresh.disabled = false;
            elements.detailDeactivate.disabled = false;
        }
    }

    function renderDetail(monitoring, observations) {
        const latest = latestObservation(observations);
        document.querySelector("#detailExpansion").textContent = `${monitoring.expansionName} · ${monitoring.expansionCode}`;
        document.querySelector("#detailTitle").textContent = monitoring.cardName;
        document.querySelector("#detailVersion").textContent = monitoring.cardVersion;
        renderCardImage(elements.detailImage, monitoring, "large");
        document.querySelector("#detailLatestPrice").textContent = observationPrice(latest, monitoring.currency);
        document.querySelector("#detailPriceSample").textContent = latest && latest.usedOffers > 0
            ? `${usedOffersLabel(latest.usedOffers)} su ${compatibleOffersLabel(latest.compatibleOffers)}`
            : "Nessuna offerta compatibile";
        document.querySelector("#detailConfidence").textContent = confidenceLabel(latest && latest.confidence);
        document.querySelector("#detailLastChecked").textContent = monitoring.lastCheckedAt
            ? formatDateTime(monitoring.lastCheckedAt)
            : "Mai";
        document.querySelector("#detailCurrency").textContent = `Valuta ${monitoring.currency}`;

        document.querySelector("#criteriaExpansion").textContent = `${monitoring.expansionName} (${monitoring.expansionCode})`;
        document.querySelector("#criteriaCard").textContent = `${monitoring.cardName} · ${monitoring.cardVersion}`;
        document.querySelector("#criteriaLanguage").textContent = languageLabel(monitoring.language);
        document.querySelector("#criteriaCondition").textContent = monitoring.condition;
        document.querySelector("#criteriaVariants").textContent = monitoringVariantLabel(monitoring);

        const errorPanel = document.querySelector("#detailErrorPanel");
        errorPanel.hidden = !monitoring.lastError;
        document.querySelector("#detailErrorText").textContent = monitoring.lastError || "";
        document.querySelector("#observationCount").textContent = observationCountLabel(observations.length);
        renderPriceChart(observations, monitoring.currency);
        document.querySelector("#detailTitle").focus({ preventScroll: true });
    }

    function renderPriceChart(observations, currency) {
        if (dashboardState.chart !== null) {
            dashboardState.chart.destroy();
            dashboardState.chart = null;
        }
        const pricedObservations = observations.filter((observation) => observation.averagePriceCents !== null);
        const chartContainer = document.querySelector("#chartContainer");
        const chartEmpty = document.querySelector("#chartEmpty");

        if (pricedObservations.length === 0 || typeof Chart === "undefined") {
            chartContainer.hidden = true;
            chartEmpty.hidden = false;
            chartEmpty.textContent = typeof Chart === "undefined"
                ? "Il componente del grafico non è disponibile. Riprova dopo aver verificato la connessione."
                : "Non ci sono ancora rilevazioni con un prezzo disponibile.";
            return;
        }

        chartContainer.hidden = false;
        chartEmpty.hidden = true;
        const context = document.querySelector("#priceChart");
        dashboardState.chart = new Chart(context, {
            type: "line",
            data: {
                labels: pricedObservations.map((observation) => formatChartDate(observation.observedAt)),
                datasets: [{
                    label: "Prezzo medio",
                    data: pricedObservations.map((observation) => Number(observation.averagePriceCents) / 100),
                    borderColor: "#d95f32",
                    backgroundColor: "rgba(217, 95, 50, 0.12)",
                    pointBackgroundColor: "#ffffff",
                    pointBorderColor: "#d95f32",
                    pointBorderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    borderWidth: 2,
                    fill: true,
                    tension: 0.25
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: "index" },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (tooltip) => `Media: ${formatAmount(tooltip.parsed.y, currency)}`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { color: "#66736e", maxRotation: 0, autoSkip: true, maxTicksLimit: 7 }
                    },
                    y: {
                        beginAtZero: false,
                        grid: { color: "rgba(23, 35, 31, 0.08)" },
                        ticks: {
                            color: "#66736e",
                            callback: (value) => formatAmount(value, currency)
                        }
                    }
                }
            }
        });
    }

    async function refreshMonitoring(monitoringId, fromDetail, sourceButton) {
        if (monitoringId === null) {
            return;
        }
        const button = fromDetail ? elements.detailRefresh : sourceButton;
        const originalLabel = button ? button.textContent : "Aggiorna";
        if (button) {
            button.disabled = true;
            button.textContent = "Aggiornamento…";
        }
        if (fromDetail) {
            setDetailStatus("Calcolo della nuova rilevazione in corso…", "info");
        }
        else {
            setDashboardStatus("Calcolo della nuova rilevazione in corso…", "info");
        }

        try {
            await requestJson(`/api/monitorings/${monitoringId}/refresh`, { method: "POST" });
            if (fromDetail) {
                await showDetail(monitoringId);
            }
            else {
                await loadDashboard();
                setDashboardStatus("Monitoraggio aggiornato correttamente.", "info");
            }
        }
        catch (error) {
            const message = errorMessage(error, "Aggiornamento non riuscito.");
            if (fromDetail) {
                setDetailStatus(message, "error");
            }
            else {
                setDashboardStatus(message, "error");
            }
        }
        finally {
            if (button) {
                button.disabled = false;
                button.textContent = originalLabel;
            }
        }
    }

    async function deactivateMonitoring(monitoringId) {
        if (monitoringId === null || !window.confirm("Disattivare questo monitoraggio? Lo storico resterà salvato.")) {
            return;
        }
        try {
            await requestJson(`/api/monitorings/${monitoringId}`, { method: "DELETE" });
            await showDashboard();
            setDashboardStatus("Monitoraggio disattivato. Lo storico è stato conservato.", "info");
        }
        catch (error) {
            const message = errorMessage(error, "Disattivazione non riuscita.");
            if (!elements.detailView.hidden) {
                setDetailStatus(message, "error");
            }
            else {
                setDashboardStatus(message, "error");
            }
        }
    }

    function latestObservation(observations) {
        return observations.length > 0 ? observations[observations.length - 1] : null;
    }

    function observationPrice(observation, fallbackCurrency) {
        return observation && observation.averagePriceCents !== null
            ? formatCents(observation.averagePriceCents, observation.currency || fallbackCurrency)
            : "Nessun dato";
    }

    function confidenceLabel(confidence) {
        return {
            HIGH: "Alta",
            MEDIUM: "Media",
            LOW: "Bassa",
            NO_DATA: "Nessun dato"
        }[confidence] || "Nessun dato";
    }

    function languageLabel(code) {
        const language = LANGUAGES.find(([candidate]) => candidate === code);
        return language ? language[1] : code;
    }

    function monitoringVariantLabel(monitoring) {
        const variants = VARIANT_LABELS
            .filter(([property]) => monitoring[property])
            .map(([, label]) => label);
        return variants.length > 0 ? variants.join(", ") : "Standard";
    }

    function observationCountLabel(count) {
        return `${count} ${count === 1 ? "rilevazione" : "rilevazioni"}`;
    }

    function usedOffersLabel(count) {
        return `${count} ${count === 1 ? "offerta usata" : "offerte usate"}`;
    }

    function compatibleOffersLabel(count) {
        return `${count} ${count === 1 ? "offerta compatibile" : "offerte compatibili"}`;
    }

    function cheapestOffersLabel(count) {
        return count === 1
            ? "Media dell’offerta più economica"
            : `Media delle ${count} offerte più economiche`;
    }

    function formatDateTime(value) {
        return new Intl.DateTimeFormat("it-IT", {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(new Date(value));
    }

    function formatChartDate(value) {
        return new Intl.DateTimeFormat("it-IT", {
            day: "2-digit",
            month: "short",
            year: "2-digit"
        }).format(new Date(value));
    }

    function formatAmount(amount, currency) {
        return new Intl.NumberFormat("it-IT", {
            style: "currency",
            currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(Number(amount));
    }

    function createElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (text !== undefined) {
            element.textContent = text;
        }
        return element;
    }

    function renderCardImage(container, card, preferredSize = "small") {
        if (!container) {
            return;
        }
        container.replaceChildren();
        const imageUrl = preferredSize === "large"
            ? card.imageUrlLarge || card.imageUrlSmall
            : card.imageUrlSmall || card.imageUrlLarge;
        if (imageUrl) {
            const image = document.createElement("img");
            image.src = imageUrl;
            image.alt = `${card.cardName} ${card.cardVersion || ""}`.trim();
            image.loading = "lazy";
            image.decoding = "async";
            image.addEventListener("error", () => renderCardImagePlaceholder(container, card), { once: true });
            container.classList.remove("is-placeholder");
            container.append(image);
            return;
        }

        renderCardImagePlaceholder(container, card);
    }

    function renderCardImagePlaceholder(container, card) {
        container.replaceChildren();
        const initials = (card.cardName || "?")
            .split(/\s+/)
            .filter(Boolean)
            .slice(0, 2)
            .map((part) => part.charAt(0))
            .join("")
            .toUpperCase();
        container.classList.add("is-placeholder");
        container.append(
            createElement("span", "card-image-placeholder-mark", initials || "?"),
            createElement("small", "", "Immagine non disponibile")
        );
    }

    function setDashboardStatus(message, type) {
        setPanelStatus(elements.dashboardStatus, message, type);
    }

    function clearDashboardStatus() {
        clearPanelStatus(elements.dashboardStatus);
    }

    function setDetailStatus(message, type) {
        setPanelStatus(elements.detailStatus, message, type);
    }

    function clearDetailStatus() {
        clearPanelStatus(elements.detailStatus);
    }

    function setPanelStatus(panel, message, type) {
        panel.textContent = message;
        panel.classList.toggle("is-info", type === "info");
        panel.hidden = false;
    }

    function clearPanelStatus(panel) {
        panel.hidden = true;
        panel.textContent = "";
        panel.classList.remove("is-info");
    }

    function selectedExpansion() {
        return state.expansions.find((expansion) => expansion.id === state.expansionId);
    }

    function selectedBlueprint() {
        return state.blueprints.find((blueprint) =>
            blueprint.id === state.blueprintId && blueprint.expansionId === state.expansionId);
    }

    async function requestJson(url, options = {}) {
        let response;
        const method = (options.method || "GET").toUpperCase();
        const headers = {
            Accept: "application/json",
            ...(options.headers || {})
        };
        if (!["GET", "HEAD", "OPTIONS"].includes(method) && authState.csrfToken !== null) {
            headers[authState.csrfHeader] = authState.csrfToken;
        }
        try {
            response = await fetch(url, {
                ...options,
                credentials: "same-origin",
                headers
            });
        }
        catch (error) {
            throw new Error("Il server non è raggiungibile. Controlla la connessione e riprova.");
        }

        if (response.status === 204) {
            return null;
        }

        let body = null;
        try {
            body = await response.json();
        }
        catch (error) {
            if (response.ok) {
                throw new Error("Il server ha restituito una risposta non valida.");
            }
        }

        if (!response.ok) {
            const error = new Error((body && body.detail) || "La richiesta non è andata a buon fine.");
            error.status = response.status;
            if (response.status === 401 && !url.startsWith("/api/auth/")) {
                showAuth("La sessione è scaduta. Accedi nuovamente.");
            }
            throw error;
        }
        return body;
    }

    function setStatus(message, type) {
        elements.status.textContent = message;
        elements.status.classList.toggle("is-info", type === "info");
        elements.status.hidden = false;
    }

    function clearStatus() {
        elements.status.hidden = true;
        elements.status.textContent = "";
        elements.status.classList.remove("is-info");
    }

    function errorMessage(error, fallback) {
        return error instanceof Error && error.message ? error.message : fallback;
    }

    function numberOrNull(value) {
        if (value === "") {
            return null;
        }
        const number = Number(value);
        return Number.isSafeInteger(number) && number > 0 ? number : null;
    }
})();
