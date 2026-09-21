package org.novasos.healthysv2.patient;
import java.util.Locale; import java.util.UUID;
final class PatientNumberGenerator {private PatientNumberGenerator(){} static String generate(UUID personId){return "PAT-"+personId.toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);}}
