package com.camunda.consulting.hackdays2024;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@ZeebeSpringTest
@SpringBootTest
public class DMNTests {

  @Autowired private ZeebeClient zeebe;

  ObjectMapper objectMapper = new ObjectMapper();

  private static final Logger LOG = LoggerFactory.getLogger(DMNTests.class);

  @Test
  void testEvaluteSimpleDecision() throws Exception {

    zeebe.newDeployResourceCommand().addResourceFromClasspath("SimpleDecision.dmn").send().join();
    // Prepare data input
    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("SimpleDecision")
            .variables(Map.of("Gender", "Male", "Name", "Bob", "Children", 3))
            .send()
            .join();
    LOG.info(
        "decision result output: {}",
        decision
            .getEvaluatedDecisions()
            .get(0)
            .getMatchedRules()
            .get(0)
            .getEvaluatedOutputs()
            .get(0)
            .getOutputValue());
    assertThat(decision.getDecisionOutput()).isEqualTo("\"Bob is a dad of 3 children.\"");
  }

  public record Person(String Name, String Gender, Integer Children) {}

  @Test
  void testEvaluateSimpleDecisionWithJsonInput() {
    zeebe
        .newDeployResourceCommand()
        .addResourceFromClasspath("SimpleDecisionJsonInput.dmn")
        .send()
        .join();
    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("SimpleDecisionJsonInput")
            .variable("Person", new Person("Anne", "Female", 2))
            .send()
            .join();
    LOG.info("decision result output: {}", decision.getEvaluatedDecisions());

    assertThat(decision.getDecisionOutput()).isEqualTo("\"Anne is a mother of 2 children.\"");
  }

  @Test
  void testEvaluateLiteralExpression() {
    zeebe
        .newDeployResourceCommand()
        .addResourceFromClasspath("SimpleLiteralExpression.dmn")
        .send()
        .join();

    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("PersonDescriptionExpression")
            .variable("Person", new Person("Anne", "Female", 2))
            .send()
            .join();
    LOG.info("decision result output: {}", decision.getEvaluatedDecisions());

    assertThat(decision.getDecisionOutput()).isEqualTo("\"Anne is a mother of 2 children.\"");
  }

  @Test
  void testBKM1() {
    zeebe
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-bkm-literal-exp.dmn")
        .send()
        .join();

    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("BKMLiteralExpression")
            .variables(Map.of("x", 2, "y", 3))
            .send()
            .join();

    assertThat(decision.getDecisionOutput()).isEqualTo("5");
  }

  @Test
  void testBKM2() {
    zeebe.newDeployResourceCommand().addResourceFromClasspath("bkm-literal-exp.dmn").send().join();

    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("PersonDescription")
            .variable("Person", new Person("Bob", "Male", 3))
            .send()
            .join();

    assertThat(decision.getDecisionOutput()).isEqualTo("\"Bob is a dad of 3 children.\"");
  }

  @Test
  public void testElterngeld() throws Exception {
    DeploymentEvent deployment =
        zeebe
            .newDeployResourceCommand()
            // .addResourceFromClasspath("Elterngeld.dmn")
            .addResourceFromClasspath("Elterngeld-Camunda-Modeler.dmn")
            .send()
            .join();

    assertThat(deployment.getDecisionRequirements().size()).isEqualTo(1);

    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            // .decisionId("_92B682EF-62D8-4B5C-A599-E1ED984B5985") // ElterngeldInsgesamt
            // .decisionId("_F57FF75A-F7EF-43EB-9AE5-F66DC1422B86") // GeschwisterbonusAnspruch
            .decisionId("_65CE9A3E-E87C-453F-A6CD-21AA933E6F35") // Geschwisterbonus
            // .decisionId("_1498108C-9C36-4BA5-B659-45B762AA4C05") // HöheDesAnspruchs
            // .decisionId("_E6E816B0-28DD-4798-932F-A0E1FB51C1E2") // AnspruchSondergrund
            // .decisionId("_B4093A7C-7ACD-44A1-AEC2-25353FD4A298") // AnspruchAufElterngeld
            .variables(
                Map.of(
                    "Eltern",
                    List.of(
                        new Elter(
                            new Anspruchsbedingung(true, true, true),
                            new Sonderanspruchsgrund(true, false, false, false, false),
                            new Einkommen(30, 0, 1500, 750, 0),
                            new Elternzeitvariante(12, 0),
                            1),
                        new Elter(
                            new Anspruchsbedingung(true, true, true),
                            new Sonderanspruchsgrund(true, false, false, false, false),
                            new Einkommen(20, 0, 3000, 2500, 0),
                            new Elternzeitvariante(12, 0),
                            1)),
                    "Geschwister",
                    new Geschwister(null, 1, 1),
                    "Neugeborene",
                    new Neugeborene(1, 0)))
            .send()
            .join();

    String evaluatedDecisions = decision.getEvaluatedDecisions().toString();
    JsonNode tree = objectMapper.readTree(evaluatedDecisions);
    LOG.info("Decision output: \n{}", tree.toPrettyString());
    //    LOG.info("Decision output: \n{}", decision.getEvaluatedDecisions().size());
    assertThat(decision.getDecisionOutput()).isNotEqualTo("null");
  }

  public record Neugeborene(Integer Anzahl, Integer WochenZuFrühGeboren) {}
  ;

  public record Geschwister(
      String InsertAName,
      Integer AnzahlGeschwisterUnterDreiJahren,
      Integer AnzahlGeschwisterDreiBisFünfJahre) {}
  ;

  public record Anspruchsbedingung(
      Boolean WohnsitzInnerhalbDeutschlands,
      Boolean KindImHaushalt,
      Boolean BetreuungUndErziehung) {}
  ;

  public record Elternzeitvariante(Integer MonateBasiselterngeld, Integer MonateElterngeldPlus) {}
  ;

  public record Partnerschaftsbonus(Boolean BonusBeziehen, Integer Monate) {}
  ;

  public record Sonderanspruchsgrund(
      Boolean unterliegtDemSGBIV4,
      Boolean dienstlichVorübergehendImAusland,
      Boolean zwischenstaatlichOderÜberstaatlichTätig,
      Boolean Entwicklingshelfer,
      Boolean Missionar) {}
  ;

  public record Einkommen(
      Integer Arbeitswochenstunden,
      Integer ArbeitswochenstundenPartnerschaftsbonus,
      Integer EinkommenVorDerGeburt,
      Integer EinkommenNachDerGeburt,
      Integer Einkommenpartnerschaftsbonus) {}
  ;

  public record Elter(
      Anspruchsbedingung Anspruchsbedingung,
      Sonderanspruchsgrund Sonderanspruchsgrund,
      Einkommen Einkommen,
      Elternzeitvariante Elternzeitvariante,
      Integer GradRechtlicherVerwandschaft) {}
  ;

  public record Loan(Integer amount, Double rate, Integer term) {}
  ;

  @Test
  public void testMonthlyPayment() {
    zeebe.newDeployResourceCommand().addResourceFromClasspath("MonthlyPayment.dmn").send().join();

    EvaluateDecisionResponse decision =
        zeebe
            .newEvaluateDecisionCommand()
            .decisionId("MonthlyPayment")
            .variables(Map.of("Loan", new Loan(10000, 0.04, 120), "fee", 100))
            .send()
            .join();

    assertThat(decision.getDecisionOutput()).isEqualTo("201.25");
  }
}
