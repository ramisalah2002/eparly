import { describe, expect, it } from "vitest";
import { draftFrom, providerCreateBody, providerPatchBody, validateProvider } from "@/lib/provider-form";
import { offer } from "./fixtures";

const provider = offer().provider;

describe("validateProvider", () => {
  it("accepte un fournisseur complet", () => {
    expect(validateProvider(draftFrom(provider))).toEqual({});
  });

  it("signale nom vide, URL non HTTPS et pays invalide", () => {
    const errors = validateProvider({ name: "  ", sector: "GAS", officialWebsite: "http://exemple.fr", country: "FRA" });
    expect(Object.keys(errors).sort()).toEqual(["country", "name", "officialWebsite"]);
  });

  it("refuse une URL avec espace ou sans hôte", () => {
    expect(validateProvider({ ...draftFrom(provider), officialWebsite: "https://exemple .fr" }).officialWebsite).toBeDefined();
    expect(validateProvider({ ...draftFrom(provider), officialWebsite: "https://" }).officialWebsite).toBeDefined();
  });
});

describe("corps de requête", () => {
  it("normalise la création (espaces, pays en majuscules)", () => {
    expect(
      providerCreateBody({ name: " Free ", sector: "MOBILE_PLAN", officialWebsite: " https://free.fr ", country: "fr" }),
    ).toEqual({ name: "Free", sector: "MOBILE_PLAN", officialWebsite: "https://free.fr", country: "FR" });
  });

  it("n'envoie en modification que les champs changés, avec la version attendue", () => {
    expect(providerPatchBody(provider, { ...draftFrom(provider), name: "Nouveau nom", country: "be" })).toEqual({
      expectedVersion: provider.version,
      name: "Nouveau nom",
      country: "BE",
    });
  });

  it("renvoie null si rien ne change, espaces et casse compris", () => {
    expect(providerPatchBody(provider, { ...draftFrom(provider), name: ` ${provider.name} `, country: "fr" })).toBeNull();
  });

  it("envoie le pays quand il était absent côté API", () => {
    expect(providerPatchBody({ ...provider, country: null }, draftFrom({ ...provider, country: null }))).toEqual({
      expectedVersion: provider.version,
      country: "FR",
    });
  });
});
